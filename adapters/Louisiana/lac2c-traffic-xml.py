from datetime import datetime
from datetime import timedelta
from datetime import timezone
import gzip
import os
import requests
import csv


def xml_segment(segment, tag, start_pos):
	start_tag = '<' + tag + '>'
	start_pos = segment.find(start_tag, start_pos)
	if start_pos < 0:
		return (start_pos, '')

	start_pos += len(start_tag)
	end_tag = '</' + tag + '>'
	end_pos = segment.find(end_tag, start_pos)
	if end_pos < 0:
		return (end_pos, '')

	return (end_pos + len(end_tag), segment[start_pos:end_pos].strip())


def convert_timestamp(datetime_str):
	start_pos, date_str = xml_segment(datetime_str, 'date', 0)
	start_pos, time_str = xml_segment(datetime_str, 'time', start_pos)
	start_pos, offset_str = xml_segment(datetime_str, 'offset', start_pos)
	ts_str = date_str + 'T' + time_str[:6] + offset_str
	return datetime.strptime(ts_str, '%Y%m%dT%H%M%S%z')


def load_metadata(csv_file):
	oMap = {}
	with open(csv_file, encoding='utf-8-sig') as file:
		reader = csv.reader(file)
		reader.__next__() # skip header
		for row in reader:
			oMap[row[0]] = (row[1], row[2])

	return oMap


def save_detector_data(now, url, path_format, name_format):
	detector_data = {}

	file_path = now.strftime(path_format)
	os.makedirs(file_path, exist_ok=True)

	detector_data_xml = ''
	file_name = file_path + '/' + now.strftime(name_format)
	response = requests.get(url, timeout=(2,2), verify=False)
	if response.status_code == 200:
		detector_data_xml = response.text
		with gzip.open(file_name, mode='wb', compresslevel=9) as file:
			file.write(response.content)

	outer_pos, detector_data_detail_xml = xml_segment(detector_data_xml, 'detector-data-detail', 0)
	while outer_pos >= 0:
		inner_pos, device_status = xml_segment(detector_data_detail_xml, 'device-status', 0)
		if inner_pos >= 0 and device_status == 'on':
			inner_pos, detector_id = xml_segment(detector_data_detail_xml, 'detector-id', 0)
			inner_pos, vehicle_speed = xml_segment(detector_data_detail_xml, 'vehicle-speed', 0)
			inner_pos, vehicle_count = xml_segment(detector_data_detail_xml, 'vehicle-count', 0)
			inner_pos, vehicle_occupancy = xml_segment(detector_data_detail_xml, 'vehicle-occupancy', 0)
			inner_pos, time_xml = xml_segment(detector_data_detail_xml, 'start-time', 0)
			start_ts = convert_timestamp(time_xml)
			inner_pos, time_xml = xml_segment(detector_data_detail_xml, 'end-time', 0)
			end_ts = convert_timestamp(time_xml)
			detector_data[detector_id] = (start_ts, end_ts, vehicle_speed, vehicle_count, vehicle_occupancy)

		outer_pos, detector_data_detail_xml = xml_segment(detector_data_xml, 'detector-data-detail', outer_pos)

	return detector_data


if __name__ == '__main__':
	now = datetime.now(timezone.utc).replace(second=0, microsecond=0)
	metadata_file = '<path-to-metadata-file>'
	data_url = '<url-for-download>'
	data_file_path = '<path-for-csv-data>/%Y%m/%Y%m%d'
	data_file_path_xml = '<path-for-xml-data>/data/%Y%m/%Y%m%d'
	data_file_name_xml = 'ladotd_speed_%Y%m%d%H%M.xml.gz'
	data_file_name_csv = 'ladotd_speed_{}_{}_{}.csv.gz'
	time_format = '%Y%m%d%H%M'
	data_time_format = '%Y-%m-%dT%H:%M:%S'

	file_path = now.strftime(data_file_path)
	os.makedirs(file_path, exist_ok=True)
	file_name = file_path + '/' + data_file_name_csv.format((now - timedelta(minutes = 2)).strftime(time_format), (now - timedelta(minutes = 1)).strftime(time_format), now.strftime(time_format))
	detector_map = load_metadata(metadata_file)
	detector_data = save_detector_data(now, data_url, data_file_path_xml, data_file_name_xml)
	with gzip.open(file_name, mode='wb', compresslevel=9) as file:
		file.write(b'"version 1.0"\n')
		file.write(b'"id"|"description"|"start_time (UTC)"|"end_time (UTC)"|"speed (kph)"|"volume (veh)"|"occupancy (%)"|"location ([lon,lat],...)"\n')
		for sId, oData in detector_data.items():
			if not sId in detector_map or (len(oData[2]) == 0 and oData[3] == '0' and oData[4] == '0'):
				continue
			oLocation = detector_map[sId]
			file.write(sId.encode())
			file.write(b'|')
			file.write(b'|') # no description
			file.write(oData[0].astimezone(timezone.utc).strftime(data_time_format).encode())
			file.write(b'|')
			file.write(oData[1].astimezone(timezone.utc).strftime(data_time_format).encode())
			file.write(b'|')
			file.write(oData[2].encode())
			file.write(b'|')
			file.write(oData[3].encode())
			file.write(b'|')
			file.write(oData[4].encode())
			file.write(b'|')
			file.write('[{},{}]\n'.format(oLocation[0], oLocation[1]).encode())
