import requests
import os
import time
from datetime import datetime
from dotenv import load_dotenv
from engine import calculate_truerank

load_dotenv()

SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")
ROBOT_EVENTS_KEY = os.environ.get("ROBOT_EVENTS_KEY")

re_headers = {"Authorization": f"Bearer {ROBOT_EVENTS_KEY}", "Accept": "application/json"}
supabase_headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}", "Content-Type": "application/json", "Prefer": "resolution=merge-duplicates"}

TARGET_SEASONS = [197, 240, 190, 181, 173, 154, 139, 130, 125, 119, 115, 110, 102, 92, 85, 73]
ACTIVE_SEASONS = []

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
                print("\n⚠️ Rate limited! Sleeping 5s...")
                time.sleep(5)
                continue
            if res.status_code != 200:
                current_page += 1
                continue
            data = res.json()
            if 'data' in data: results.extend(data['data'])
            meta = data.get('meta', {})
            if current_page == 1: last_page = meta.get('last_page', 1)
            print("■", end="", flush=True)
            current_page += 1
        except: current_page += 1
    print()
    return results

def upload_in_batches(endpoint, payload, headers, batch_size=1000):
    for i in range(0, len(payload), batch_size):
        batch = payload[i:i + batch_size]
        res = requests.post(endpoint, json=batch, headers=headers)
        if res.status_code in [200, 201]: print(f"   -> Uploaded batch of {len(batch)}")
        else: print(f"   -> ❌ Batch failed: {res.text}")

def check_season_completed(season_id):
    url = f"{SUPABASE_URL}/rest/v1/season_status?season_id=eq.{season_id}&select=is_completed"
    try:
        res = requests.get(url, headers=supabase_headers)
        if res.status_code == 200:
            data = res.json()
            if data and data[0].get('is_completed') == True: return True
    except: pass
    return False

def mark_season_completed(season_id):
    url = f"{SUPABASE_URL}/rest/v1/season_status"
    payload = {"season_id": season_id, "is_completed": True, "last_updated": datetime.utcnow().isoformat()}
    requests.post(url, json=payload, headers=supabase_headers)

def run_worker():
    print(f"🤖 Starting Pipeline for {len(TARGET_SEASONS)} seasons...")
    for season_id in TARGET_SEASONS:
        print(f"\n==================================================")
        print(f"📅 COMMENCING PROCESSING FOR SEASON ID: {season_id}")
        print(f"==================================================")

        if check_season_completed(season_id):
            print(f"✅ Season {season_id} is marked as COMPLETED. Skipping.")
            continue

        print("📡 Fetching event manifest...", end=" ")
        events_url = f"https://www.robotevents.com/api/v2/events?season[]={season_id}&per_page=250"
        all_events = fetch_all_pages(events_url)
        today = datetime.now().strftime('%Y-%m-%d')
        past_events = [e for e in all_events if e.get('start', '')[:10] < today]
        print(f"✅ Found {len(past_events)} completed events.\n")

        if not past_events: continue

        all_teams, all_matches = [], []
        for idx, event in enumerate(past_events):
            print(f"[{idx+1}/{len(past_events)}] Extracting: {event.get('sku')}")
            for div in event.get('divisions', []):
                print("   ↳ Roster: ", end="", flush=True)
                all_teams.extend([r["team"] for r in fetch_all_pages(f"https://www.robotevents.com/api/v2/events/{event['id']}/divisions/{div['id']}/rankings?per_page=250")])
                print("   ↳ Matches: ", end="", flush=True)
                all_matches.extend(fetch_all_pages(f"https://www.robotevents.com/api/v2/events/{event['id']}/divisions/{div['id']}/matches?per_page=250"))
            time.sleep(3)

        if not all_matches: continue

        print(f"\n🧠 Running TrueRank & OPR Engine on {len(all_matches)} matches...")
        final_elo_data = calculate_truerank(all_teams, all_matches)

        for data in final_elo_data: data["season_id"] = season_id

        print("\n☁️ Connecting to Supabase...")
        print(f"☁️ Step 1/2: Pushing global roster...")
        teams_payload = []
        seen_teams = set()
        for t in all_teams:
            t_id = t.get("name") or t.get("code") or t.get("number")
            if not t_id or t_id in seen_teams: continue
            seen_teams.add(t_id)
            teams_payload.append({"id": t_id, "team_name": t.get("team_name", t.get("name", "Unknown Name")), "grade_level": t.get("grade", "Unknown")})
        upload_in_batches(f"{SUPABASE_URL}/rest/v1/teams", teams_payload, supabase_headers)

        print(f"\n☁️ Step 2/2: Uploading {len(final_elo_data)} Elo & OPR records...")
        upload_in_batches(f"{SUPABASE_URL}/rest/v1/global_truerank", final_elo_data, supabase_headers)

        if season_id not in ACTIVE_SEASONS:
            print(f"\n🔒 Locking Season {season_id} as COMPLETED.")
            mark_season_completed(season_id)

if __name__ == "__main__":
    run_worker()