import pytest
from unittest.mock import patch
import sys
import os
import requests

# Ensure pytest can find raw_data_builder.py
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from raw_data_builder import fetch_all_pages

@patch('raw_data_builder.requests.get')
def test_fetch_all_pages_server_error(mock_get):
    """
    Tests if the pipeline survives when the RobotEvents API crashes.
    We 'mock' requests.get to return a 500 Server Error.
    """
    # Configure the fake API to simulate a 500 Internal Server Error
    mock_get.return_value.status_code = 500
    mock_get.return_value.json.return_value = {}

    # Call your actual function with a fake URL
    result = fetch_all_pages("https://www.robotevents.com/api/v2/fake_endpoint")

    # Because last_page defaults to 1, and the error increments current_page to 2,
    # the loop should safely break and return an empty list.
    assert result == [], "Pipeline crashed or returned junk data instead of handling the API error!"
    assert mock_get.call_count == 1, "The API was not called exactly once."


@patch('raw_data_builder.requests.get')
def test_fetch_all_pages_network_timeout(mock_get):
    """
    Tests if the pipeline handles a complete internet connection timeout.
    Instead of returning a status code, the request completely fails.
    """
    # Configure the fake API to trigger a Python Timeout exception
    mock_get.side_effect = requests.exceptions.Timeout("Connection timed out")

    # Call your actual function
    result = fetch_all_pages("https://www.robotevents.com/api/v2/fake_endpoint")

    # The 'except Exception as e:' block should catch this and return []
    assert result == [], "Pipeline completely crashed on a network timeout!"