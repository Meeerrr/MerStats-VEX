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

re_headers = {
    "Authorization": f"Bearer {ROBOT_EVENTS_KEY}",
    "Accept": "application/json"
}

# Added the 25-26 Push Back season (ID 199) to the front of the line!
TARGET_SEASONS = [199, 190, 181, 173, 154, 139, 130, 125, 119, 115, 110, 102, 92, 85, 73]

def fetch_all_pages(base_url):
    results = []
    current_page = 1
    last_page = 1

    while current_page <= last_page:
        separator = "&" if "?" in base_url else "?"
        paginated_url = f"{base_url}{separator}page={current_page}"

        try:
            res = requests.get(paginated_url, headers=re_headers, timeout=15)

            if res.status_code == 429:
                print("\n⚠️ Rate limited by VEX! Sleeping for 5 seconds...")
                time.sleep(5)
                continue

            if res.status_code != 200:
                print(f"\n⚠️ API Error {res.status_code}. Skipping page {current_page}...")
                current_page += 1
                continue

            data = res.json()
            if 'data' in data:
                results.extend(data['data'])

            meta = data.get('meta', {})
            if current_page == 1:
                last_page = meta.get('last_page', 1)

            print("■", end="", flush=True)
            current_page += 1

        except requests.exceptions.JSONDecodeError:
            print(f"\n⚠️ VEX sent corrupted data. Skipping page {current_page}...")
            current_page += 1
        except requests.exceptions.RequestException as e:
            print(f"\n⚠️ Network disconnected. Skipping page {current_page}...")
            current_page += 1

    print()
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
    if not ROBOT_EVENTS_KEY or not SUPABASE_URL:
        print("❌ ERROR: Keys are missing! Check your .env file.")
        return

    print(f"🤖 Starting Historical TrueRank Pipeline for {len(TARGET_SEASONS)} seasons...")

    for season_id in TARGET_SEASONS:
        print(f"\n==================================================")
        print(f"📅 COMMENCING PROCESSING FOR SEASON ID: {season_id}")
        print(f"==================================================")

        print("📡 Fetching event manifest...", end=" ")
        events_url = f"https://www.robotevents.com/api/v2/events?season[]={season_id}&per_page=250"
        all_events = fetch_all_pages(events_url)

        today = datetime.now().strftime('%Y-%m-%d')
        past_events = [e for e in all_events if e.get('start', '')[:10] < today]
        print(f"✅ Found {len(past_events)} completed events.\n")

        all_teams = []
        all_matches = []

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

            time.sleep(3)

        print(f"\n🧠 Running TrueRank on {len(all_matches)} matches...")
        final_elo_data = calculate_truerank(all_teams, all_matches)

        for data in final_elo_data:
            data["season_id"] = season_id

        print("\n☁️ Connecting to Supabase...")
        supabase_headers = {
            "apikey": SUPABASE_KEY,
            "Authorization": f"Bearer {SUPABASE_KEY}",
            "Content-Type": "application/json",
            "Prefer": "resolution=merge-duplicates"
        }

        print(f"☁️ Step 1/2: Pushing global roster...")
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

        print(f"\n☁️ Step 2/2: Uploading {len(final_elo_data)} TrueRank records for Season {season_id}...")
        upload_in_batches(f"{SUPABASE_URL}/rest/v1/global_truerank", final_elo_data, supabase_headers)

    print("\n✅ Success! All historical seasons processed successfully.")

if __name__ == "__main__":
    run_worker()