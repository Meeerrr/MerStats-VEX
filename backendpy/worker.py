import requests
import os
from engine import calculate_truerank

# The unique web address of our Supabase project
SUPABASE_URL = os.environ.get("https://hzgvkmlonbffeuelojxv.supabase.co")

# The 'service_role' secret key (The Admin Password that allows Python to bypass Row Level Security and WRITE to the database)
SUPABASE_KEY = os.environ.get("secret")


ROBOT_EVENTS_KEY = os.environ.get("Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIzIiwianRpIjoiNTE0MjRkZDAyMDQ1MzA1MGVlZGYzZjM2OGY4ODQ3NDFhZGMwOTZjMjExMTA1OWRiYWE0MDFiMDJjY2VhZGE1ZGE0OTc4ZmY1ZDhlYjNkNzciLCJpYXQiOjE3NzUxNDUzMzAuNTI1OTI5LCJuYmYiOjE3NzUxNDUzMzAuNTI1OTMwOSwiZXhwIjoyNzIxOTE2NTMwLjUyMDU3NzksInN1YiI6IjEyNjM3MyIsInNjb3BlcyI6W119.hZuGrp7w4nO6m8NOgm3iKsFZ5l85PV8W5yhP9mgnpgXuJCL71Tg9IWR4xTIdD1uVVEKAZGPhuwRybTGHO2jp171zVZFU-U4K7w0m_wj1xzqu8-yIHW6uzhxypMAF6Nixc__vT0gKcEuLZE_WECxRjd3PQTtZ7uOT8bu81bXpCWSSd-GtItSaGpZ10CeQ42VV0aFzNm2uMePINW2N-gMJxjbrKEIqcloNw8R8K_xdrpL8O8VwQJoQFPMmq24fLSmcsWX8l4aoDqwHWsKx19JNj80h6lwge5WdGcWxmYU1b1crESb8Od69GV78QwnM0QqgIu7KRbSqiBeOVE4GyxxIVflnQPoiRSRKz1k5I1dLEzoBwavG-LX2QZjtZZTL5gFLOc_rqJLb6Y4rANZvJBpRfFUJ0MNRn0Ert7Up5ahDZqkU3_67_CQAomZITP8MVK7O__btFScefR4m9mffete2ad2MSbY3PiN9sFFp3dFhYGnC9MJKCvYn-6jzll-4oJpzj41QvKLe8Dwpkqz3DxRV8v9dgQcgifcEUi8yix1V8_YtX-VlPiH3TKdDm6gMMQewxK-25KiajV7wSm2ZLfj_KW2CLc5qyr09egDWdhAJhn_hrkKqlaCjP6CFM998HIIkwPzW81bh3SmqRgzvobg7fgpRrA2CuvU96ZIWDplS8Mg")
TARGET_SKU = "RE-VRC-23-3690"

re_headers = {
    "Authorization": f"Bearer {ROBOT_EVENTS_KEY}",
    "Accept": "application/json"
}

def run_worker():
    print(f"🤖 Starting TrueRank Worker for SKU: {TARGET_SKU}")

    print("📡 Fetching Event Metadata...")
    event_res = requests.get(f"https://www.robotevents.com/api/v2/events?sku[]={TARGET_SKU}", headers=re_headers).json()

    if not event_res.get("data"):
        print("❌ Could not find event. Check your RobotEvents API Key.")
        return

    event_id = event_res["data"][0]["id"]
    divisions = event_res["data"][0].get("divisions", [])

    all_teams = []
    all_matches = []

    for div in divisions:
        div_id = div["id"]
        print(f"📡 Fetching Division {div_id} Rankings and Matches...")

        rank_res = requests.get(f"https://www.robotevents.com/api/v2/events/{event_id}/divisions/{div_id}/rankings?per_page=250", headers=re_headers).json()
        if "data" in rank_res:
            all_teams.extend([r["team"] for r in rank_res["data"]])

        match_res = requests.get(f"https://www.robotevents.com/api/v2/events/{event_id}/divisions/{div_id}/matches?per_page=250", headers=re_headers).json()
        if "data" in match_res:
            all_matches.extend(match_res["data"])

    print("🧠 Running 3-Pass TrueRank Algorithm...")
    final_elo_data = calculate_truerank(all_teams, all_matches)

    print("☁️ Connecting to Supabase...")
    supabase_headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "resolution=merge-duplicates"
    }

    print("☁️ Step 1/2: Pushing roster to 'teams' table...")
    teams_payload = []
    seen_teams = set()

    for t in all_teams:
        t_id = t.get("name") or t.get("code") or t.get("number")
        if not t_id or t_id in seen_teams:
            continue

        seen_teams.add(t_id)
        teams_payload.append({
            "id": t_id,
            "team_name": t.get("team_name", t.get("name", "Unknown Name")),
            "grade_level": t.get("grade", "Unknown Grade")
        })

    teams_endpoint = f"{SUPABASE_URL}/rest/v1/teams"
    teams_res = requests.post(teams_endpoint, json=teams_payload, headers=supabase_headers)

    if teams_res.status_code not in [200, 201]:
        print(f"❌ Teams upload failed: {teams_res.text}")
        return

    print(f"☁️ Step 2/2: Uploading {len(final_elo_data)} Elo records to 'global_truerank'...")
    elo_endpoint = f"{SUPABASE_URL}/rest/v1/global_truerank"
    elo_res = requests.post(elo_endpoint, json=final_elo_data, headers=supabase_headers)

    if elo_res.status_code in [200, 201]:
        print(f"✅ Success! Engine cycle complete.")
    else:
        print(f"❌ Elo upload failed with status {elo_res.status_code}: {elo_res.text}")

if __name__ == "__main__":
    run_worker()