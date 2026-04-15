import requests
import os
import time
from datetime import datetime
from dotenv import load_dotenv
from engine import calculate_truerank

load_dotenv()

# --- 1. CONFIGURATION ---
SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")
ROBOT_EVENTS_KEY = os.environ.get("ROBOT_EVENTS_KEY")

TARGET_SEASON_ID = 190

re_headers = {
    "Authorization": f"Bearer {ROBOT_EVENTS_KEY}",
    "Accept": "application/json"
}

def fetch_all_pages(base_url):
    """Handles pagination safely by manually forcing the page number."""
    results = []
    current_page = 1
    last_page = 1

    while current_page <= last_page:
        # Force the page parameter so RobotEvents never drops our season filter
        separator = "&" if "?" in base_url else "?"
        paginated_url = f"{base_url}{separator}page={current_page}"

        res = requests.get(paginated_url, headers=re_headers)

        if res.status_code == 429:
            print("\n⚠️ Rate limited! Sleeping for 5 seconds...")
            time.sleep(5)
            continue

        data = res.json()
        if 'data' in data:
            results.extend(data['data'])

        # Update our target on the very first loop
        meta = data.get('meta', {})
        if current_page == 1:
            last_page = meta.get('last_page', 1)

        print("■", end="", flush=True)
        current_page += 1

    print() # Move to the next line when done
    return results

def upload_in_batches(endpoint, payload, headers, batch_size=1000):
    for i in range(0, len(payload), batch_size):
        batch = payload[i:i + batch_size]
        res = requests.post(endpoint, json=batch, headers=headers)
        if res.status_code in [200, 201]:
            print(f"   -> Uploaded batch of {len(batch)} records.")
        else:
            print(f"   -> ❌ Batch failed: {res.text}")

def run_worker():
    print(f"🤖 Starting Full Season TrueRank Pipeline for Season: {TARGET_SEASON_ID}")

    if not ROBOT_EVENTS_KEY or not SUPABASE_URL:
        print("❌ ERROR: Keys are missing! Check your .env file.")
        return

    # --- 2. FETCH ALL EVENTS ---
    print("📡 Fetching event manifest for the season", end=" ")
    events_url = f"https://www.robotevents.com/api/v2/events?season[]={TARGET_SEASON_ID}&per_page=250"
    all_events = fetch_all_pages(events_url)

    today = datetime.now().strftime('%Y-%m-%d')
    past_events = [e for e in all_events if e.get('start', '')[:10] < today]
    print(f"✅ Found {len(past_events)} completed events.\n")

    all_teams = []
    all_matches = []

    # --- 3. EXTRACT MATCHES ---
    for idx, event in enumerate(past_events):
        event_id = event['id']
        sku = event.get('sku')
        print(f"[{idx+1}/{len(past_events)}] Extracting: {sku}")

        for div in event.get('divisions', []):
            div_id = div['id']

            print("   ↳ Downloading Roster: ", end="", flush=True)
            rank_url = f"https://www.robotevents.com/api/v2/events/{event_id}/divisions/{div_id}/rankings?per_page=250"
            all_teams.extend([r["team"] for r in fetch_all_pages(rank_url)])

            print("   ↳ Downloading Matches: ", end="", flush=True)
            match_url = f"https://www.robotevents.com/api/v2/events/{event_id}/divisions/{div_id}/matches?per_page=250"
            all_matches.extend(fetch_all_pages(match_url))

        time.sleep(0.5)

    # --- 4. CRUNCH THE MATH ---
    print(f"\n🧠 Extraction complete. Running TrueRank on {len(all_matches)} matches...")
    final_elo_data = calculate_truerank(all_teams, all_matches)

    for data in final_elo_data:
        data["season_id"] = TARGET_SEASON_ID

    # --- 5. UPLOAD TO DATABASE ---
    print("\n☁️ Connecting to Supabase...")
    supabase_headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "resolution=merge-duplicates"
    }

    print(f"☁️ Step 1/2: Pushing global roster ({len(all_teams)} raw teams found)...")
    teams_payload = []
    seen_teams = set()
    for t in all_teams:
        t_id = t.get("name") or t.get("code") or t.get("number")
        if not t_id or t_id in seen_teams: continue
        seen_teams.add(t_id)
        teams_payload.append({
            "id": t_id,
            "team_name": t.get("team_name", t.get("name", "Unknown Name")),
            "grade_level": t.get("grade", "Unknown Grade")
        })
    upload_in_batches(f"{SUPABASE_URL}/rest/v1/teams", teams_payload, supabase_headers)

    print(f"\n☁️ Step 2/2: Uploading {len(final_elo_data)} TrueRank records...")
    upload_in_batches(f"{SUPABASE_URL}/rest/v1/global_truerank", final_elo_data, supabase_headers)

    print("\n✅ Success! Global Season Pipeline complete.")

if __name__ == "__main__":
    run_worker()