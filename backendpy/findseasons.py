import requests
import os
from dotenv import load_dotenv

load_dotenv()
headers = {
    "Authorization": f"Bearer {os.environ.get('ROBOT_EVENTS_KEY')}",
    "Accept": "application/json"
}

# Fetch the master list of seasons from RobotEvents
res = requests.get("https://www.robotevents.com/api/v2/seasons?per_page=250", headers=headers).json()

print("🔍 Searching RobotEvents for Push Back Season IDs...\n")
for season in res.get('data', []):
    name = season.get('name', '')
    # Filter for the current active season
    if "Push Back" in name or "2025-2026" in name:
        program = season.get('program', {}).get('code', 'Unknown')
        print(f"🏆 Program: {program} | Season: {name} | ---> ID: {season['id']}")