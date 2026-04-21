import requests
import os
import time
from datetime import datetime
from dotenv import load_dotenv
from fast_worker import run_fast_compute

load_dotenv()

# ==========================================
# API KEYS & HEADERS
# ==========================================
SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")
ROBOT_EVENTS_KEY = os.environ.get("ROBOT_EVENTS_KEY")

re_headers = {
    "Authorization": f"Bearer {ROBOT_EVENTS_KEY}",
    "Accept": "application/json"
}
supabase_headers = {
    "apikey": SUPABASE_KEY,
    "Authorization": f"Bearer {SUPABASE_KEY}",
    "Content-Type": "application/json",
    "Prefer": "resolution=merge-duplicates"
}

# The master list of seasons you care about.
# You can add new ones here, and the Smart Router will handle the rest!
TARGET_SEASONS = [197,190,240, 181, 173, 154, 139, 130, 125, 119, 115, 110, 102, 92,]

def fetch_all_pages(base_url):
    """Handles pagination and aggressive rate limiting from RobotEvents"""
    results = []
    current_page = 1
    last_page = 1

    while current_page <= last_page:
        separator = "&" if "?" in base_url else "?"
        paginated_url = f"{base_url}{separator}page={current_page}"
        try:
            res = requests.get(paginated_url, headers=re_headers, timeout=15)

            if res.status_code == 429:
                print("\n⚠️ Rate limited! Sleeping 5s...")
                time.sleep(5)
                continue

            if res.status_code != 200:
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

        except Exception as e:
            print(f"Error: {e}")
            current_page += 1

    print()
    return results

def upload_to_supabase(payload, batch_size=1000):
    endpoint = f"{SUPABASE_URL}/rest/v1/raw_matches"
    for i in range(0, len(payload), batch_size):
        batch = payload[i:i + batch_size]
        res = requests.post(endpoint, json=batch, headers=supabase_headers)
        if res.status_code in [200, 201]:
            print(f"   ☁️ Upserted batch of {len(batch)} matches to Cloud.")
        else:
            print(f"   ❌ Batch failed: {res.text}")

# ==========================================
# 🔥 THE SMART ROUTER LOGIC
# ==========================================
def get_seasons_to_process():
    print("🧠 Initializing Smart Router...")
    print("📡 Fetching official season status from RobotEvents...")

    # 1. Get the official start/end dates for every season
    all_re_seasons = fetch_all_pages("https://www.robotevents.com/api/v2/seasons")
    season_meta_map = {s['id']: s for s in all_re_seasons}

    seasons_to_run = []

    for season_id in TARGET_SEASONS:
        meta = season_meta_map.get(season_id)
        if not meta:
            continue

        # 2. Check if the season is ACTIVE by looking at the official end date
        is_active = False
        end_date_str = meta.get('end')

        if end_date_str:
            # Parse '2024-05-30T00:00:00Z' into a date object we can compare to today
            end_date = datetime.strptime(end_date_str[:10], "%Y-%m-%d")
            if datetime.now() <= end_date:
                is_active = True

        if is_active:
            print(f"   🟢 Season {season_id} is ACTIVE. Added to queue.")
            seasons_to_run.append(season_id)
        else:
            # 3. If archived, check if we already downloaded it by asking Supabase for just 1 row
            db_check = requests.get(f"{SUPABASE_URL}/rest/v1/raw_matches?season_id=eq.{season_id}&select=id&limit=1", headers=supabase_headers)

            if len(db_check.json()) == 0:
                print(f"   🟡 Season {season_id} is ARCHIVED but missing data. Added to queue (One-Time Download).")
                seasons_to_run.append(season_id)
            else:
                print(f"   ⚪ Season {season_id} is ARCHIVED and completed. Skipped.")

    return seasons_to_run

# ==========================================

def build_cache(active_seasons):
    if not active_seasons:
        print("✅ No new seasons require data fetching!")
        return

    print(f"\n🚀 Starting Database Cache Builder for {len(active_seasons)} seasons...")

    for season_id in active_seasons:
        print(f"\n==================================================")
        print(f"📅 CACHING SEASON ID: {season_id}")
        print(f"==================================================")

        print("📡 Fetching event manifest...", end=" ")
        events_url = f"https://www.robotevents.com/api/v2/events?season[]={season_id}&per_page=250"
        all_events = fetch_all_pages(events_url)
        print(f"✅ Found {len(all_events)} events.\n")

        if not all_events:
            continue

        matches_payload = []

        for idx, event in enumerate(all_events):
            print(f"[{idx+1}/{len(all_events)}] Extracting Matches: {event.get('sku')}", end=" ")
            # 1. Grab the official level from RobotEvents (e.g., "World", "Signature", "National")
            event_level = event.get('level', 'Local')

            for div in event.get('divisions', []):
                div_matches = fetch_all_pages(f"https://www.robotevents.com/api/v2/events/{event['id']}/divisions/{div['id']}/matches?per_page=250")

                for m in div_matches:
                    alliances = m.get("alliances", [])
                    if len(alliances) < 2:
                        continue

                    red_all = alliances[0] if alliances[0].get("color") == "red" else alliances[1]
                    blue_all = alliances[0] if alliances[0].get("color") == "blue" else alliances[1]

                    red_score = red_all.get("score", 0)
                    blue_score = blue_all.get("score", 0)

                    if red_score == 0 and blue_score == 0:
                        continue

                    red_teams = [t.get("team", {}).get("name") for t in red_all.get("teams", [])]
                    blue_teams = [t.get("team", {}).get("name") for t in blue_all.get("teams", [])]

                    micro_match = {
                        "rs": red_score,
                        "rt": red_teams,
                        "bs": blue_score,
                        "bt": blue_teams
                    }

                    matches_payload.append({
                        "id": m["id"],
                        "season_id": season_id,
                        "match_data": micro_match,
                        "lvl": event_level
                    })

            time.sleep(1)

            if len(matches_payload) >= 5000:
                print("\n   📦 Payload full. Uploading chunk...")
                upload_to_supabase(matches_payload)
                matches_payload = []

        if matches_payload:
            print(f"\n   📦 Finalizing Season {season_id} uploads...")
            upload_to_supabase(matches_payload)

if __name__ == "__main__":
    # 1. Run the Smart Router
    seasons_to_process = get_seasons_to_process()

    # 2. Only scrape the seasons that actually need it
    build_cache(seasons_to_process)

    # 3. Only crunch math for the seasons that were scraped
    if seasons_to_process:
        print("\n✅ CACHE COMPLETE. Automatically launching the Lightning Engine...")
        time.sleep(2)
        run_fast_compute(seasons_to_process)
    else:
        print("\n✅ Smart Router determined no updates were needed today. Pipeline resting.")