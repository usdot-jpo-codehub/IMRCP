from datetime import datetime
from datetime import timezone
import gzip
import json
import sys


def convert_timestamp(datetime_str):
    end = len(datetime_str)
    ts_str = datetime_str[:19] + ' ' + datetime_str[end - 6:end - 3] + datetime_str[end - 2:end]
    return datetime.strptime(ts_str, '%Y-%m-%dT%H:%M:%S %z')


def convert_type(type_str):
    if type_str.find('Road') >= 0:
        return '"workzone"'
    else:
        return '"incident"'


def convert_lanes(lane_str):
    if lane_str.find('lane') >= 0:
        return '1'
    elif lane_str.find('closed') >= 0:
        return '99'
    else:
        return '98'


# Get the JSON file name from the command line argument
file_name = sys.argv[1]
csv_name = sys.argv[2]

# Create an empty dictionary
records = {}

# Read the JSON file
try:
    with open(file_name) as file:
        data = json.load(file)
except FileNotFoundError:
    print(f"File '{file_name}' not found.")
    sys.exit(1)
except json.JSONDecodeError:
    print(f"Unable to parse JSON from '{file_name}'.")
    sys.exit(1)

# Process each JSON object in the "value" array
for obj in data.get("value", []):
    # Extract the required values
    obj_id = obj.get("Id", "")
    start_datetime = convert_timestamp(obj.get("StartDateTime", ""))
    end_datetime = convert_timestamp(obj.get("EndDateTime", ""))
    obj_type = obj.get("Type", "")
    subject = obj.get("Subject", "")
    last_modified = convert_timestamp(obj.get("LastModified", ""))
    lanes_affected = obj.get("LanesAffected", "")

    # Process each location in the "Locations" array
    locations = []
    for location in obj.get("Locations", []):
        # Extract the latitude and longitude values
        latitude = location.get("Latitude", 0.0)
        longitude = location.get("Longitude", 0.0)

        # Create a tuple from the latitude and longitude
        locations.append((longitude, latitude))

    # Create a Python object with the extracted values
    python_obj = {
        "StartDateTime": start_datetime,
        "EndDateTime": end_datetime,
        "Type": obj_type,
        "Subject": subject,
        "LastModified": last_modified,
        "LanesAffected": lanes_affected,
        "Locations": locations
    }

    records[obj_id] = python_obj

data_time_format = '%Y-%m-%dT%H:%M:%S'
with gzip.open(csv_name, mode='wb', compresslevel=9) as file:
        file.write(b'"version 1.0"\n')
        file.write(b'"id"|"event_type"|"description"|"start_time (UTC)"|"end_time (UTC)"|"update_time (UTC)"|"lanes_affected"|"speed_limit (kph)"|"location ([lon,lat],...)"\n')
        for sId, event in records.items():
                locations = event["Locations"]
                if len(locations) == 0:
                        continue
                temp = '"' + sId + '"'
                file.write(temp.encode())
                file.write(b'|')
                file.write(convert_type(event["Type"]).encode())
                file.write(b'|')
                temp = '"' + event["Subject"] + '"'
                file.write(temp.encode())
                file.write(b'|')
                file.write(event["StartDateTime"].astimezone(timezone.utc).strftime(data_time_format).encode())
                file.write(b'|')
                file.write(event["EndDateTime"].astimezone(timezone.utc).strftime(data_time_format).encode())
                file.write(b'|')
                file.write(event["LastModified"].astimezone(timezone.utc).strftime(data_time_format).encode())
                file.write(b'|')
                file.write(convert_lanes(event["LanesAffected"]).encode())
                file.write(b'|')
                file.write(''.encode())
                file.write(b'|')
                for x in range(len(locations)):
                    location = locations[x]
                    if x > 0:
                        file.write(','.encode())

                    file.write('[{:.7f},{:.7f}]'.format(location[0], location[1]).encode())

                file.write('\n'.encode())
