import requests
import os
import time
import json
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

# 199 = Push Back, 190 = High Stakes, 181 = Over Under, etc.
TARGET_SEASONS = [199, 190, 181, 173, 154, 139, 130, 125, 119, 115, 110, 102, 92, 85, 73]

# Seasons that are currently happening. We ALWAYS want to fetch new events for these.
ACTIVE_SEASONS = [190, 199]

# Setup the Local Cache Directory
CACHE_DIR = "event_cache"
os.makedirs(CACHE_DIR, exist_ok=True)

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
                print("\n⚠️ Rate limited! Sleeping for 5 seconds...")
                time.sleep(5)
                continue

            if res.status_code != 200:
                print(f"\n⚠️ API Error {res.status_code}. Skipping page...")
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
            print(f"\n⚠️ Corrupted data. Skipping page...")
            current_page += 1
        except requests.exceptions.RequestException as e:
            print(f"\n⚠️ Network disconnected. Skipping page...")
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

def check_season_in_db(season_id, headers):
    """Checks if a season already has data in Supabase."""
    url = f"{SUPABASE_URL}/rest/v1/global_truerank?season_id=eq.{season_id}&select=team_id&limit=1"
    try:
        res = requests.get(url, headers=headers)
        if res.status_code == 200 and len(res.json()) > 0:
            return True
    except:
        pass
    return False

def run_worker():
    if not ROBOT_EVENTS_KEY or not SUPABASE_URL:
        print("❌ ERROR: Keys are missing! Check your .env file.")
        return

    supabase_headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "resolution=merge-duplicates"
    }

    print(f"🤖 Starting Smart Incremental Pipeline for {len(TARGET_SEASONS)} seasons...")

    for season_id in TARGET_SEASONS:
        print(f"\n==================================================")
        print(f"📅 COMMENCING PROCESSING FOR SEASON ID: {season_id}")
        print(f"==================================================")

        # 1. THE CLOUD SYNC CHECK
        if season_id not in ACTIVE_SEASONS:
            if check_season_in_db(season_id, supabase_headers):
                print(f"✅ Season {season_id} is already fully archived in Supabase. Skipping!")
                continue

        print("📡 Fetching event manifest...", end=" ")
        events_url = f"https://www.robotevents.com/api/v2/events?season[]={season_id}&per_page=250"
        all_events = fetch_all_pages(events_url)

        today = datetime.now().strftime('%Y-%m-%d')
        past_events = [e for e in all_events if e.get('start', '')[:10] < today]
        print(f"✅ Found {len(past_events)} completed events.\n")

        all_teams = []
        all_matches = []

        # 2. THE LOCAL DELTA CACHE
        for idx, event in enumerate(past_events):
            event_id = event['id']
            sku = event.get('sku')

            cache_path = os.path.join(CACHE_DIR, f"{sku}.json")

            # If we already downloaded this specific tournament, load it from disk!
            if os.path.exists(cache_path):
                try:
                    with open(cache_path, "r") as f:
                        cached_data = json.load(f)
                        all_teams.extend(cached_data["teams"])
                        all_matches.extend(cached_data["matches"])
                    print(f"[{idx+1}/{len(past_events)}] {sku} -> ⚡ Loaded from Local Cache")
                    continue
                except:
                    pass # If the cache file is corrupted, we just fall through and re-download it

            # If it's a new tournament, download it from the API
            print(f"[{idx+1}/{len(past_events)}] Extracting: {sku}")
            event_teams = []
            event_matches = []

            for div in event.get('divisions', []):
                div_id = div['id']

                print("   ↳ Downloading Roster: ", end="", flush=True)
                rank_url = f"https://www.robotevents.com/api/v2/events/{event_id}/divisions/{div_id}/rankings?per_page=250"
                event_teams.extend([r["team"] for r in fetch_all_pages(rank_url)])

                print("   ↳ Downloading Matches: ", end="", flush=True)
                match_url = f"https://www.robotevents.com/api/v2/events/{event_id}/divisions/{div_id}/matches?per_page=250"
                event_matches.extend(fetch_all_pages(match_url))

            # Save the newly downloaded event to our hard drive so we never have to download it again
            with open(cache_path, "w") as f:
                json.dump({"teams": event_teams, "matches": event_matches}, f)

            all_teams.extend(event_teams)
            all_matches.extend(event_matches)

            time.sleep(3) # Polite delay for API

        # --- CRUNCH THE MATH ---
        if not all_matches:
            print("⚠️ No matches found to process. Moving on.")
            continue

        print(f"\n🧠 Running TrueRank on {len(all_matches)} matches...")
        final_elo_data = calculate_truerank(all_teams, all_matches)

        for data in final_elo_data:
            data["season_id"] = season_id

        # --- UPLOAD TO DATABASE ---
        print("\n☁️ Connecting to Supabase...")

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