import requests
import os
import time
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
    "Prefer": "resolution=merge-duplicates" # Crucial: Allows safe re-runs
}

TARGET_SEASONS = [240, 181, 173, 154, 139, 130, 125, 119, 115, 110, 102, 92, 85, 73]

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

            # Handle Rate Limits cleanly
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
    """Pushes data to Supabase in massive chunks"""
    endpoint = f"{SUPABASE_URL}/rest/v1/raw_matches"

    for i in range(0, len(payload), batch_size):
        batch = payload[i:i + batch_size]
        res = requests.post(endpoint, json=batch, headers=supabase_headers)

        if res.status_code in [200, 201]:
            print(f"   ☁️ Upserted batch of {len(batch)} matches to Cloud.")
        else:
            print(f"   ❌ Batch failed: {res.text}")

def build_cache():
    print(f"🚀 Starting Database Cache Builder for {len(TARGET_SEASONS)} seasons...")

    for season_id in TARGET_SEASONS:
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

            for div in event.get('divisions', []):
                div_matches = fetch_all_pages(f"https://www.robotevents.com/api/v2/events/{event['id']}/divisions/{div['id']}/matches?per_page=250")

                # Format and Micro-Compress for Supabase DB
                for m in div_matches:
                    alliances = m.get("alliances", [])
                    if len(alliances) < 2:
                        continue # Skip invalid matches

                    # Sort alliances safely (RobotEvents sometimes scrambles the array order)
                    red_all = alliances[0] if alliances[0].get("color") == "red" else alliances[1]
                    blue_all = alliances[0] if alliances[0].get("color") == "blue" else alliances[1]

                    red_score = red_all.get("score", 0)
                    blue_score = blue_all.get("score", 0)

                    # Skip unplayed matches
                    if red_score == 0 and blue_score == 0:
                        continue

                    red_teams = [t.get("team", {}).get("name") for t in red_all.get("teams", [])]
                    blue_teams = [t.get("team", {}).get("name") for t in blue_all.get("teams", [])]

                    # 🔬 THE MICRO-JSON PAYLOAD
                    micro_match = {
                        "rs": red_score,
                        "rt": red_teams,
                        "bs": blue_score,
                        "bt": blue_teams
                    }

                    matches_payload.append({
                        "id": m["id"],
                        "season_id": season_id,
                        "match_data": micro_match
                    })

            time.sleep(1) # Be nice to the API between events

            # If our payload gets huge, upload it to clear memory
            if len(matches_payload) >= 5000:
                print("\n   📦 Payload full. Uploading chunk...")
                upload_to_supabase(matches_payload)
                matches_payload = [] # Clear the list

        # Upload any remaining matches at the end of the season
        if matches_payload:
            print(f"\n   📦 Finalizing Season {season_id} uploads...")
            upload_to_supabase(matches_payload)

if __name__ == "__main__":
    # 1. Run the scraper
    build_cache()

    # 2. Automatically trigger the math engine
    print("\n✅ CACHE COMPLETE. Automatically launching the Lightning Engine...")
    time.sleep(2) # Give the database a brief second to settle

    run_fast_compute()