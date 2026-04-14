import requests
from supabase import create_client, Client
from engine import calculate_truerank

# --- 1. CONFIGURATION ---
# Your Supabase credentials (Settings -> API)
SUPABASE_URL = "https://hzgvkmlonbffeuelojxv.supabase.co"
SUPABASE_KEY = "sb_publishable_aEbWOGCjVYkkiG2LS_Zczg_mYlnRsz1"

# Your RobotEvents credentials
ROBOT_EVENTS_KEY = "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIzIiwianRpIjoiNTE0MjRkZDAyMDQ1MzA1MGVlZGYzZjM2OGY4ODQ3NDFhZGMwOTZjMjExMTA1OWRiYWE0MDFiMDJjY2VhZGE1ZGE0OTc4ZmY1ZDhlYjNkNzciLCJpYXQiOjE3NzUxNDUzMzAuNTI1OTI5LCJuYmYiOjE3NzUxNDUzMzAuNTI1OTMwOSwiZXhwIjoyNzIxOTE2NTMwLjUyMDU3NzksInN1YiI6IjEyNjM3MyIsInNjb3BlcyI6W119.hZuGrp7w4nO6m8NOgm3iKsFZ5l85PV8W5yhP9mgnpgXuJCL71Tg9IWR4xTIdD1uVVEKAZGPhuwRybTGHO2jp171zVZFU-U4K7w0m_wj1xzqu8-yIHW6uzhxypMAF6Nixc__vT0gKcEuLZE_WECxRjd3PQTtZ7uOT8bu81bXpCWSSd-GtItSaGpZ10CeQ42VV0aFzNm2uMePINW2N-gMJxjbrKEIqcloNw8R8K_xdrpL8O8VwQJoQFPMmq24fLSmcsWX8l4aoDqwHWsKx19JNj80h6lwge5WdGcWxmYU1b1crESb8Od69GV78QwnM0QqgIu7KRbSqiBeOVE4GyxxIVflnQPoiRSRKz1k5I1dLEzoBwavG-LX2QZjtZZTL5gFLOc_rqJLb6Y4rANZvJBpRfFUJ0MNRn0Ert7Up5ahDZqkU3_67_CQAomZITP8MVK7O__btFScefR4m9mffete2ad2MSbY3PiN9sFFp3dFhYGnC9MJKCvYn-6jzll-4oJpzj41QvKLe8Dwpkqz3DxRV8v9dgQcgifcEUi8yix1V8_YtX-VlPiH3TKdDm6gMMQewxK-25KiajV7wSm2ZLfj_KW2CLc5qyr09egDWdhAJhn_hrkKqlaCjP6CFM998HIIkwPzW81bh3SmqRgzvobg7fgpRrA2CuvU96ZIWDplS8Mg"
TARGET_SKU = "RE-VRC-23-3690" # The event we want to process

headers = {
    "Authorization": ROBOT_EVENTS_KEY,
    "Accept": "application/json"
}

def run_worker():
    print(f"🤖 Starting TrueRank Worker for SKU: {TARGET_SKU}")

    # --- 2. FETCH DATA FROM ROBOT EVENTS ---
    print("📡 Fetching Event Metadata...")
    event_res = requests.get(f"https://www.robotevents.com/api/v2/events?sku[]={TARGET_SKU}", headers=headers).json()

    if not event_res.get("data"):
        print("❌ Could not find event.")
        return

    event_id = event_res["data"][0]["id"]
    divisions = event_res["data"][0].get("divisions", [])

    all_teams = []
    all_matches = []

    for div in divisions:
        div_id = div["id"]
        print(f"📡 Fetching Division {div_id} Rankings and Matches...")

        # Get Teams in this division
        rank_res = requests.get(f"https://www.robotevents.com/api/v2/events/{event_id}/divisions/{div_id}/rankings?per_page=250", headers=headers).json()
        if "data" in rank_res:
            all_teams.extend([r["team"] for r in rank_res["data"]])

        # Get Matches in this division
        match_res = requests.get(f"https://www.robotevents.com/api/v2/events/{event_id}/divisions/{div_id}/matches?per_page=250", headers=headers).json()
        if "data" in match_res:
            all_matches.extend(match_res["data"])

    # --- 3. CRUNCH THE MATH ---
    print("🧠 Running 3-Pass TrueRank Algorithm...")
    final_elo_data = calculate_truerank(all_teams, all_matches)

    # --- 4. PUSH TO SUPABASE ---
    print("☁️ Connecting to Supabase...")
    supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)

    print(f"🚀 Uploading {len(final_elo_data)} teams to 'global_truerank' table...")

    # .upsert() is incredibly powerful: it updates existing teams, and inserts new ones!
    response = supabase.table("global_truerank").upsert(final_elo_data).execute()

    print("✅ Worker finished successfully!")

if __name__ == "__main__":
    run_worker()