from datetime import datetime, timedelta
import json
import logging
import math
import os
import pickle
import sys
import warnings
import numpy as np
import pandas as pd
import torch


import imrcp_implementation_realtime as realtime

import mlph_functions as mlph

from model_training import train_oneshot
from model_training import train_online


def loadData(sPath):
	return pd.read_csv(sPath, encoding='utf_8')

def runRealTime(sDir, start_time, sPickleDir):
	oPredictions = []
	oHistDat = loadData('{}mlp_input.csv'.format(sDir))
	oGroupsById = oHistDat.groupby('Id')
	idlist = [sId for sId in oGroupsById.groups.keys()]
	nIdCount = len(idlist)
	
	# load the decision tree model
	tree = None
	with open('{}decision_tree.pickle'.format(sPickleDir),'rb') as file:
		tree = pickle.load(file) # here is a re-trained decision tree model, load this before starting the online prediction
	
	# Load variables from the data file
	loaded_data = None
	with open('{}mlp_python_data.pkl'.format(sPickleDir), "rb") as file:
		loaded_data = pickle.load(file)
	horz = 120
	resolution = 15
# 	nObs = int(horz / resolution)

	long_ts = pd.DataFrame(columns=['timestamplist', 'speed'])
	for sGroupName, oDf in oGroupsById:
		oDf = oDf.reset_index(drop=True)
		try:
			if len(oDf['Speed'].dropna()) < 5:
				oPred = None
			else:
				oPred = realtime.pred_short(horz, resolution, start_time, oDf, long_ts, loaded_data, tree)
		except BaseException as oEx:
			logging.error('error with {}: {}'.format(sGroupName, str(oEx)))
			logging.exception('')
			oPred = None
		oPredictions.append(oPred)

	with open('{}mlp_output.csv'.format(sDir), 'w') as oFile:
		for i in range(0, nIdCount):
			if oPredictions[i] is None:
				continue
			oFile.write(idlist[i])
			for dVal in oPredictions[i]:
				oFile.write(',{:.2f}'.format(dVal))
			oFile.write('\n')


def runLongTs(sDir, start_time):
	oPredictions = []
	oHistDat = loadData('{}mlp_lts_input.csv'.format(sDir))
	oGroupsById = oHistDat.groupby('Id')
	idlist = [sId for sId in oGroupsById.groups.keys()]
	nIdCount = len(idlist)
	
	sTsFormat = '%Y-%m-%d %H:%M'
	oStartTime = datetime.strptime(start_time, sTsFormat)
	oBeginningOfDay = oStartTime - timedelta(hours=oStartTime.hour + 1)
	startt = oBeginningOfDay.strftime(sTsFormat)
	for sGroupName, oDf in oGroupsById:
		oDf = oDf.reset_index(drop=True)
		try:
			if len(oDf['Speed'].dropna()) < 5:
				oPred = None
			else:
				oPred = realtime.long_ts_update(startt, oDf)
				oPred = np.concatenate([oPred[1728:2016], oPred, oPred[0:288]])
		except BaseException as oEx:
			logging.error('error with {}: {}'.format(sGroupName, str(oEx)))
			logging.exception('')
			oPred = None
		oPredictions.append(oPred)
	
	oStartOfForecast = oBeginningOfDay - timedelta(hours=23, minutes=55)
	oFiveMinutes = timedelta(minutes=5)
	with open('{}mlp_lts_output.csv'.format(sDir), 'w') as oFile:
		oFile.write('Id,timestamplist,speed\n')
		for i in range(0, nIdCount):
			if oPredictions[i] is None:
				continue
			oTs = datetime.fromtimestamp(oStartOfForecast.timestamp())
			sId = idlist[i]
			for dVal in oPredictions[i]:
				oFile.write(sId)
				oFile.write(',{}'.format(oTs.strftime(sTsFormat)))
				oTs = oTs + oFiveMinutes
				oFile.write(',{:.2f}\n'.format(dVal))
			oFile.write('\n')


def runOneshot(sDir, start_time, sPickleDir, sLtsDirectory):
	oPredictions = []
	loaded_data = None
	with open('{}mlp_python_data.pkl'.format(sPickleDir), "rb") as file:
		loaded_data = pickle.load(file)
	oHistDat = loadData('{}mlp_oneshot_input.csv'.format(sDir))
	oGroupsById = oHistDat.groupby('Id')
	idlist = [sId for sId in oGroupsById.groups.keys()]
	nIdCount = len(idlist)
	oLongTs = loadData('{}mlp_lts_output.csv'.format(sLtsDirectory))
	oLongTsById = oLongTs.groupby('Id')
# 	nWeatherIntvl = 60
# 	nOutputs = int(60 / nWeatherIntvl * 168)
	for sGroupName, oDf in oGroupsById:
		oDf = oDf.reset_index(drop=True)
		try:
			if sGroupName in oLongTsById.groups:
				oLts = oLongTsById.get_group(sGroupName)
				oLts = oLts.reset_index(drop=True)
				#oPred = realtime.oneshot(nWeatherIntvl, start_time, oDf, oLts, loaded_data)
				oPred = realtime.oneshot_new(oDf, start_time, oLts, loaded_data)
				bAllNans = True
				for dVal in oPred:
					if not math.isnan(dVal):
						bAllNans = False
						break
				if bAllNans:
					oPred = None
			else:
				oPred = None
		except BaseException as oEx:
			logging.error('error with {}: {}'.format(sGroupName, str(oEx)))
			logging.exception('')
			oPred = None
		oPredictions.append(oPred)

	
	
	with open('{}mlp_oneshot_output.csv'.format(sDir), 'w') as oFile:
		for i in range(0, nIdCount):
			if oPredictions[i] is None:
				continue
			oFile.write(idlist[i])
			for dVal in oPredictions[i]:
				oFile.write(',{:.2f}'.format(dVal))
			oFile.write('\n')
	



def runOneshotScenarios(sDir, start_time, sPickleDir, nOutputIndex, sLtsDirectory):
	oPredictions = []
	loaded_data = None
	with open('{}mlp_python_data.pkl'.format(sPickleDir), "rb") as file:
		loaded_data = pickle.load(file)
	oHistDat = loadData('{}mlp_oneshot_input.csv'.format(sDir))
	oGroupsById = oHistDat.groupby('Id')
	idlist = [sId for sId in oGroupsById.groups.keys()]
	nIdCount = len(idlist)
	oLongTs = loadData('{}mlp_lts_output.csv'.format(sLtsDirectory))
	oLongTsById = oLongTs.groupby('Id')
# 	nWeatherIntvl = 60
# 	nOutputs = int(60 / nWeatherIntvl * 168)
	for sGroupName, oDf in oGroupsById:
		oDf = oDf.reset_index(drop=True)
		try:
			if sGroupName in oLongTsById.groups:
				oLts = oLongTsById.get_group(sGroupName)
				oLts = oLts.reset_index(drop=True)
				#oPred = realtime.oneshot(nWeatherIntvl, start_time, oDf, oLts, loaded_data)
				oPred = realtime.oneshot_new(oDf, start_time, oLts, loaded_data)
				bAllNans = True
				for dVal in oPred[nOutputIndex:nOutputIndex + 25]:
					if not math.isnan(dVal):
						bAllNans = False
						break
				if bAllNans:
					oPred = None
			else:
				oPred = None
		except BaseException as oEx:
			logging.error('error with {}: {}'.format(sGroupName, str(oEx)))
			logging.exception('')
			oPred = None
		oPredictions.append(oPred)
		
	with open('{}mlp_oneshot_output.csv'.format(sDir), 'w') as oFile:
		for i in range(0, nIdCount):
			if oPredictions[i] is None:
				continue
			oFile.write(idlist[i])
			for dVal in oPredictions[i][nOutputIndex:nOutputIndex + 25]:
				oFile.write(',{:.2f}'.format(dVal))
			oFile.write('\n')

def runHurOnline(sDir, start_time, sModelDir):
	oPredictions = {}
	oErrors = []
	dummy_input_lstm = loadData('{}dummy_input_lstm.csv'.format(sDir))
	link_speed_next7d = loadData('{}link_speed_next7d.csv'.format(sDir))
	modelpath = sModelDir + 'online_model_{}hour.pth'
	oGroupsById = link_speed_next7d.groupby('Id')
	idlist = [sId for sId in oGroupsById.groups.keys()]
	for sId in idlist:
		oPredictions[sId] = []
	for horizon in range(1, 7): # define the prediction horizon (e.g., 1,2,3,4,5,6)
		try:
			model_online = mlph.initialize_online(14, modelpath.format(horizon))

			for sGroupName, oDf in oGroupsById:
				try:
					oDf = oDf.reset_index(drop=True)
					online_output = mlph.online_predict(start_time, oDf, dummy_input_lstm, model_online).detach().numpy().flatten()[0]
					oPredictions[sGroupName].append(online_output)
				except BaseException as oEx:
					logging.error('error with {}: {}'.format(sGroupName, str(oEx)))
					logging.exception('')
					oPredictions[sGroupName].append(math.nan)

		except Exception as e:
			for sId in idlist:
				oPredictions[sId].append(math.nan)
			logging.exception('')

	with open('{}hur_output.csv'.format(sDir), 'w') as oFile:
		for sId in idlist:
			oFile.write(sId)
			for dVal in oPredictions[sId]:
				oFile.write(',{:.2f}'.format(dVal))
			oFile.write('\n')
	
	if len(oErrors) == 0:
		return ''
	
	return str(oErrors)

def runHurOneshot(sDir, sModelDir):
	oPredictions = []
	oneshot_input = loadData('{}hos_input.csv'.format(sDir)) # load features for 7-day congestion status prediction
	dummy_input_oneshot = loadData('{}dummy_input_oneshot.csv'.format(sDir)) # load dummy input
	link_speed_past7d = loadData('{}link_speed_past7d.csv'.format(sDir)) # load past 7-day speed for 7-day speed pattern generation
	modelpath = sModelDir + 'oneshot_model.pth'
	model_oneshot = mlph.initialize_oneshot(15, modelpath)
	oOneshotGroupsById = oneshot_input.groupby('Id')
	oSpeedPastGroupsById = link_speed_past7d.groupby('Id')
	idlist = [sId for sId in oOneshotGroupsById.groups.keys()]
	nIdCount = len(idlist)
	for sGroupName, oDf in oOneshotGroupsById:
		try:
			oDf = oDf.reset_index(drop=True)
			oPast = oSpeedPastGroupsById.get_group(sGroupName)
			oPast = oPast.reset_index(drop=True)
			oPredictions.append(mlph.oneshot_predict(oDf, dummy_input_oneshot, oPast, model_oneshot))
		except KeyError:
			oPredictions.append(None)
			pass
		except BaseException as oEx:
			oPredictions.append(None)
			logging.error('error with {}: {}'.format(sGroupName, str(oEx)))
			logging.exception('')

	with open('{}hos_output.csv'.format(sDir), 'w') as oFile:
		for i in range(0, nIdCount):
			if oPredictions[i] is None:
				continue
			oFile.write(idlist[i])
			for dVal in oPredictions[i]:
				oFile.write(',{:.2f}'.format(dVal))
			oFile.write('\n')


def trainHur(sMetadataFile):
	logging.info('training!@#')
	oMetadataObj = None
	with open(sMetadataFile, 'r') as oFile:
		oMetadataObj = json.load(oFile)
	
	oStats = oMetadataObj['stats']
	data_mata = loadData(oMetadataObj['linkid'])
	freeway_name = oMetadataObj['freeway_name']
	filtered_mata = mlph.filter_mata(data_mata, freeway_name)
	file_pattern_prefix = oMetadataObj['file_pattern_prefix']
	folder_path = oMetadataObj['folder_path']
	lf_time = oMetadataObj['lf_time']
	category = oMetadataObj['category']
	lf_zone = oMetadataObj['lf_zone']
	lf_coord = oMetadataObj['lf_coord']
	hurricane = oMetadataObj['hurricane']
	logging.info('Starting training for {}!@#'.format(sMetadataFile))
	data_w_feature_7d = None
	data_w_feature_8d = None
	data_6hr_7d_all = None
	for i in range(len(lf_time)):
		logging.info('Assembling data for storm {}!@#'.format(hurricane[i]))
		oMeansAndStds = oStats[hurricane[i]]
		try:
			combined_past7d,combined_next7d,combined_next8d = mlph.extract_csvfile(folder_path[i],file_pattern_prefix,hurricane[i], category[i] ,lf_time[i],lf_zone[i],lf_coord[i])
		except BaseException:
			logging.exception('')
			logging.error('Failed to load data for {}!@#'.format(hurricane[i]))
			continue

		data_w_feature_7d_single = mlph.spatial_temporal_feature(combined_next7d,hurricane[i],lf_time[i],category[i],lf_zone[i],lf_coord[i],filtered_mata)
		data_w_feature_8d_single = mlph.spatial_temporal_feature(combined_next8d,hurricane[i],lf_time[i],category[i],lf_zone[i],lf_coord[i],filtered_mata)
		
		data_6hr_7d_all_single = mlph.oneshot_annotate(data_w_feature_7d_single,combined_past7d,lf_time[i], oMeansAndStds, hurricane[i])
		
		if data_w_feature_7d is None:
			data_w_feature_7d = data_w_feature_7d_single
			data_w_feature_8d = data_w_feature_8d_single
			data_6hr_7d_all = data_6hr_7d_all_single
		else:
			data_w_feature_7d = pd.concat([data_w_feature_7d, data_w_feature_7d_single])
			data_w_feature_8d = pd.concat([data_w_feature_8d, data_w_feature_8d_single])
			data_6hr_7d_all = pd.concat([data_6hr_7d_all, data_6hr_7d_all_single])
	
	if data_w_feature_7d is None:
		logging.error('No data available to train model!@#')
		return
	data_w_feature_7d = pd.DataFrame(data_w_feature_7d)
	data_w_feature_8d = pd.DataFrame(data_w_feature_8d)
	data_6hr_7d_all = pd.DataFrame(data_6hr_7d_all)
	sOutputDir = oMetadataObj['networkdir']
	os.makedirs(sOutputDir, exist_ok=True)
	logging.info('Starting training for oneshot model!@#')
	data_6hr_7d_all.to_csv('{}data_6hr_7d_all.csv'.format(sOutputDir), index=False)
	data_w_feature_8d.to_csv('{}data_w_feature_8d.csv'.format(sOutputDir), index=False)
	dummy_oneshot = mlph.create_dummy_oneshot(data_w_feature_8d,data_6hr_7d_all)
	dummy_oneshot.to_csv('{}dummy_oneshot.csv'.format(sOutputDir),index=False)
	model_oneshot = train_oneshot(data_6hr_7d_all,dummy_oneshot)
	
	logging.info('Saving trained oneshot model!@#')
	torch.save(model_oneshot.state_dict(), '{}oneshot_model.pth.tmp'.format(oMetadataObj['networkdir']))
	
	dummy_online = mlph.create_dummy_online(data_w_feature_8d,data_6hr_7d_all)
	dummy_online.to_csv('dummy_online.csv',index=False)
	
	for horizon in range(1, 7):
		logging.info('Starting online model for hour {}!@#'.format(horizon))
		model_online = train_online(data_6hr_7d_all,data_w_feature_8d,dummy_online,horizon)
		torch.save(model_online.state_dict(), '{}online_model_{}hour.pth'.format(oMetadataObj['networkdir'], horizon))
		logging.info('Finished online model for hour {}!@#'.format(horizon))
	
	os.rename('{}oneshot_model.pth.tmp'.format(oMetadataObj['networkdir']), '{}oneshot_model.pth'.format(oMetadataObj['networkdir']))

		
if __name__ == '__main__':
	warnings.filterwarnings("ignore")
	sLogLevel = sys.argv[1].lower()
	oLevel = logging.NOTSET
	if sLogLevel == 'debug':
		oLevel = logging.DEBUG
	elif sLogLevel == 'info':
		oLevel = logging.INFO
	elif sLogLevel == 'warning':
		oLevel = logging.WARNING
	elif sLogLevel == 'error':
		oLevel = logging.ERROR
	elif sLogLevel == 'critical':
		oLevel = logging.CRITICAL
	
	
	logging.basicConfig(stream=sys.stdout, level=oLevel, format=str(os.getpid()) + ' - %(levelname)s - %(message)s')
	try:
		sFunction = sys.argv[2].lower()
		sDirectory = sys.argv[3]
		sStartTime = sys.argv[4]
		sModelDir = sys.argv[5]
		nForecastOffset = int(sys.argv[6])
		sLtsDirectory = sys.argv[7]
		logging.debug(sys.argv)
		if sFunction == 'rt':
			runRealTime(sDirectory, sStartTime, sModelDir)
		elif sFunction == 'lts':
			runLongTs(sDirectory, sStartTime)
		elif sFunction == 'os':
			if nForecastOffset == -1:
				runOneshot(sDirectory, sStartTime, sModelDir, sLtsDirectory)
			else:
				runOneshotScenarios(sDirectory, sStartTime, sModelDir, nForecastOffset, sLtsDirectory)
		elif sFunction == 'hur':
			runHurOnline(sDirectory, sStartTime, sModelDir)
		elif sFunction == 'hos':
			runHurOneshot(sDirectory, sModelDir)
		elif sFunction == 'trainhur':
			trainHur(sDirectory)
	except BaseException:
		logging.exception('')
		
