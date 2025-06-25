# -*- coding: utf-8 -*-
"""
Created on Wed May 17 23:52:16 2023

@author: qhjia
"""

import numpy as np
import pandas as pd
from statsmodels.tsa.arima.model import ARIMA
from pmdarima.arima import auto_arima
import random



#%%
def long_ts_update(startt, histdatafm):
	startt = histdatafm['Timestamp'].tolist().index(startt)
	datmp = histdatafm.iloc[startt-2015:startt+1, :]
	
# =============================================================================
# 	raw_wkd = datmp[datmp['DayOfWeek']==2]['Speed'].values
# 	means = []
# 	for i in range(288):
# 		temp = []
# 		for j in raw_wkd[i:i+288*5:288]:
# 			if not math.isnan(j):
# 				temp.append(j)
# 		means.append(np.mean(temp))
# 
# 	sss1 = np.array(means)
# =============================================================================
	raw_wkd = datmp[datmp['DayOfWeek']==2]['Speed'].dropna().values # select all weekdays from the last 7 days
	sss1 = np.array([np.mean(raw_wkd[i:i+288*5:288]) for i in range(288)])
	ss1 = pd.Series(np.tile(sss1, 5), dtype='double')
	ss1_rol = ss1.rolling(12).mean()
	ss1_rol[0] = ss1[0]
	ss1_rol.interpolate(inplace=True)
	ss1_rol_smooth = ss1_rol[0:len(ss1_rol):12]

	arima_model =  auto_arima(ss1_rol_smooth,start_p=0, d=1, start_q=0, 
								max_p=5, max_d=5, max_q=5, start_P=0, 
								D=1, start_Q=0, max_P=5, max_D=5,
								max_Q=5, m=24, seasonal=True )
	pred = arima_model.predict(n_periods = 24)
	pred = np.repeat(pred, 12)
	rdm = np.random.uniform(low = 0.995, high = 1.005, size=288)  
	spd_forecastwkday = pred*rdm
	
	datmp = histdatafm.iloc[startt-2015:startt+1, :]
	raw_wkn = datmp[datmp['DayOfWeek']==1]['Speed'].dropna().values   # select all weekend days from the last 7 days
	sss2 = np.array([np.mean(raw_wkn[i:i+288*2:288]) for i in range(288)])

# =============================================================================
# 	raw_test = datmp[datmp['DayOfWeek']==1]['Speed'].values
# 	means = []
# 	for i in range(288):
# 		temp = []
# 		for j in raw_test[i:i+288*2:288]:
# 			if not math.isnan(j):
# 				temp.append(j)
# 		means.append(np.mean(temp))
# 	sss3 = np.array(means)
# =============================================================================
	
	ss2 = pd.Series(np.tile(sss2, 2), dtype='double')
	ss2_rol = ss2.rolling(12).mean()
	ss2_rol[0] = ss2[0]
	ss2_rol.interpolate(inplace=True)
	ss2_rol_smooth = ss2_rol[0:len(ss2_rol):12]
	
	arima_model =  auto_arima(ss2_rol_smooth,start_p=0, d=1, start_q=0, 
								max_p=5, max_d=5, max_q=5, start_P=0, 
								D=1, start_Q=0, max_P=5, max_D=5,
								max_Q=5, m=24, seasonal=True )
	pred = arima_model.predict(n_periods = 24)
	pred = np.repeat(pred, 12)
	rdm = np.random.uniform(low = 0.995, high = 1.005, size=288)
	spd_forecastwkend = pred*rdm

	# Find index of first Saturday
	wknd_idx = np.where(datmp['DayOfWeek'] == 1)[0][0]
	if wknd_idx == 0: # first day is saturday
		long_big = np.concatenate([np.tile(spd_forecastwkend, 2), 
									np.tile(spd_forecastwkday, 5)])
	elif wknd_idx == 288: # first day is friday
		long_big = np.concatenate([np.tile(spd_forecastwkday, 1), 
									np.tile(spd_forecastwkend, 2), 
									np.tile(spd_forecastwkday, 4)])
	elif wknd_idx == 576: # first day is thursday
		long_big = np.concatenate([np.tile(spd_forecastwkday, 2), 
									np.tile(spd_forecastwkend, 2), 
									np.tile(spd_forecastwkday, 3)])
	elif wknd_idx == 864: # first day is wednesday
		long_big = np.concatenate([np.tile(spd_forecastwkday, 3), 
									np.tile(spd_forecastwkend, 2), 
									np.tile(spd_forecastwkday, 2)])
	elif wknd_idx == 1152: # first day is tuesday
		long_big = np.concatenate([np.tile(spd_forecastwkday, 4), 
									np.tile(spd_forecastwkend, 2), 
									np.tile(spd_forecastwkday, 1)])
	elif wknd_idx == 1440: # first day is monday
		long_big = np.concatenate([np.tile(spd_forecastwkday, 5), 
									np.tile(spd_forecastwkend, 2)])
		
	else: # first day is sunday
		long_big = np.concatenate([[np.tile(spd_forecastwkend, 1), 
									np.tile(spd_forecastwkday, 5), 
									np.tile(spd_forecastwkend, 1)]])
		
	return long_big



#%%
# horz: prediction horizon  (minute), e.g., 60 minutes, 120 minutes
# resolution: prediction resolution within each horizon  (minute), e.g., 15 minutes

def pred_short(horz,resolution, startt, histdatafm, long_ts_predfm, loaded_data, tree):
	# Access the variables
	mc_contra = loaded_data["markov_contra"]
	markov_ohio = loaded_data["markov_ohio"]
	markov_vsl = loaded_data["markov_vsl"]
	markov_hsr = loaded_data["markov_hsr"]
	centers_contra = loaded_data["centers_contra"]
	centers_hsr = loaded_data["centers_hsr"]
	centers_ohio = loaded_data["centers_ohio"]
	centers_vsl = loaded_data["centers_vsl"]

	
	
	arima_order = (0,1,2) # parameters for decision tree
	drop_col = ['Timestamp','Id','Flow','Speed','Occupancy','road','contraflow', 'vsl','hsr'] 

	dattemp = histdatafm.copy()
	long_ts_predd = long_ts_predfm.copy()
	intvl = horz // 5
	rel = []
	indexx = dattemp.index[dattemp['Timestamp'] == startt][0]

	predspd = []
	predspdd = []
	xxx = indexx
	tempsp = dattemp.loc[xxx-1, 'Speed']
	spmean = dattemp.loc[(xxx-287):(xxx-1), 'Speed'].dropna().mean()
	msss = centers_ohio
	msss_vsl = centers_vsl
	msss_hsr = centers_hsr
	# dattemp = dattemp.drop(columns=['Timestamp','Id','Flow','Speed','Occupancy','road','vsl','hsr'])
	for i in range(xxx, xxx+intvl, int(resolution/5)):
		# i = xxx
		currenttime = dattemp.loc[i, 'Timestamp']
		tempid = np.where(long_ts_predd['timestamplist'] == currenttime)[0]
		if tempid.size > 0:
			spd_forecastt = long_ts_predd.loc[tempid[0], 'speed']
		else:
			spd_forecastt = 200
		
		# First, determine if the contraflow ever existed in the last 5 minutes. Make sure 'contraflow' is one of the column names.
		if 'contraflow' in dattemp.columns and dattemp.loc[xxx-1, 'contraflow'] > 0:
			# If contraflow is implemented, determine which contraflow scenario it meets
			if dattemp.loc[xxx-1, 'contraflow'] == 1:
				if dattemp.loc[xxx-5:xxx, 'Speed'].min() >= 20:
					mc = mc_contra[0]
					msss_contra = centers_contra[0]
				elif dattemp.loc[xxx-5:xxx, 'Speed'].min() >= 10 and dattemp.loc[xxx-5:xxx, 'Speed'].min() < 20:
					mc = mc_contra[1]
					msss_contra = centers_contra[1]
				else:
					mc = mc_contra[2]
					msss_contra = centers_contra[2]
			else:
				if dattemp.loc[xxx-5:xxx, 'Speed'].min() >= 20:
					mc = mc_contra[3]
					msss_contra = centers_contra[3]
				elif dattemp.loc[xxx-5:xxx, 'Speed'].min() >= 10 and dattemp.loc[xxx-5:xxx, 'Speed'].min() < 20:
					mc = mc_contra[4]
					msss_contra = centers_contra[4]
				else:
					mc = mc_contra[5]
					msss_contra = centers_contra[5]
				
			for j in range(4):
				stt = np.argmin(np.abs(msss_contra - np.array([tempsp])))
				mss = msss_contra.copy()
				mss[stt] = tempsp
				tempsp = round(np.sum(mc[stt,:] * mss), 2)
			predspd.append(tempsp)
			
		else:
			# Second, determine if the vsl ever existed in the last 30 minutes
			if len(dattemp.loc[xxx-4:xxx, 'vsl'][dattemp.loc[xxx-4:xxx, 'vsl']>0]) > 0:
				mc = markov_vsl[0]
				for j in range(4):
					stt = abs(msss_vsl - tempsp).argmin()
					mss = msss_vsl.copy()
					mss[stt] = tempsp
					tempsp = round((mc * mss).sum(), 2)
				predspd.append(tempsp)
			elif len(dattemp.loc[xxx-4:xxx, 'hsr'][dattemp.loc[xxx-4:xxx, 'hsr']>0]) > 0:
				mc = markov_hsr[0]
				for j in range(4):
					stt = abs(msss_hsr - tempsp).argmin()
					mss = msss_hsr.copy()
					mss[stt] = tempsp
					tempsp = round((mc * mss).sum(), 2)
				predspd.append(tempsp)
			else:
				if dattemp.iloc[i]["IncidentOnLink"] == 0 and dattemp.iloc[i]["IncidentDownstream"] == 0:
					if (any(dattemp.iloc[i-1:i-4]["IncidentOnLink"] == 1) or 
						any(dattemp.iloc[i-1:i-4]["IncidentDownstream"] == 1) or 
						any(dattemp.iloc[i-1:i-4]["WorkzoneOnLink"] == 1)) and dattemp.iloc[i]["Precipitation"] == 1:
						tempsp = round((6*spmean+tempsp)/7, 2)
						predspd.append(tempsp)
					elif dattemp.iloc[i]["Precipitation"] == 1:
						np.random.seed(i)
						s3 = pd.concat([dattemp.iloc[xxx-72:xxx]['Speed'].dropna(),pd.Series(predspd)]).reset_index(drop=True)
						s3 = pd.Series(s3)
						p3 = ARIMA(s3, order=arima_order).fit()
						spd_forecast3 = p3.forecast(steps=1, alpha=0.005)
						tempsp = round(spd_forecast3.values[0], 2)
						if spd_forecastt > (spmean-10):
							tempsp = round(spd_forecast3.values[0], 2)
						else:
							if len(predspd) == 0:
								if intvl == 1:
									tempsp = spd_forecastt - (long_ts_predd[tempid-1]['speed'] - dattemp.iloc[xxx-1]['Speed'])
								else:
									tempsp = spd_forecastt
							else:
								tempsp = spd_forecastt - (predspd[0] - dattemp.iloc[xxx-1]['Speed'])
						predspd.append(tempsp)
					elif dattemp.iloc[i]["Precipitation"] > 1:
						pred = tree.predict(dattemp.drop(columns=drop_col).iloc[xxx-6:xxx,:])
						t = pd.DataFrame(pred)[0].value_counts()
						if t.idxmax() == 1:
							if histdatafm.iloc[i]["Precipitation"] in [2,3,5]:
								mc = markov_ohio[0]
							else:
								mc = markov_ohio[1]
							for j in range(4):
								stt = np.argmin(np.abs(msss - np.array(tempsp)))
								mss = msss
								mss[stt] = tempsp
								tempsp = round(np.sum(mc[stt,:] * mss), 2)
							predspd.append(tempsp)
						else:
							np.random.seed(i)
							s3 = pd.concat([dattemp.iloc[xxx-72:xxx]['Speed'].dropna(),pd.Series(predspd)]).reset_index(drop=True)
							p3 = ARIMA(s3, order=arima_order).fit()
							spd_forecast3 = p3.forecast(steps=1, alpha=0.005)
							tempsp = round(spd_forecast3.values[0], 2)
							if spd_forecastt > (spmean - 10):
								tempsp = round(spd_forecast3.values[0], 2)
							else:
								if len(predspd) == 0:
									if intvl == 1:
										tempsp = spd_forecastt - (long_ts_predd[tempid-1]['speed'] - histdatafm.iloc[startt-2]['Speed'])
									else:
										tempsp = spd_forecastt
								else:
									tempsp = spd_forecastt - (predspd[0] - histdatafm.iloc[startt-2]['Speed'])
							predspd.append(tempsp)
				else:
					pred = tree.predict(dattemp.drop(columns=drop_col).iloc[xxx-6:xxx,:])
					t = pd.Series(pred).value_counts()
					if t.idxmax() == 1:
						if dattemp.loc[i,"IncidentOnLink"] == 1:
							if dattemp.loc[i,"Precipitation"] == 1 and dattemp.loc[i,"LanesClosedOnLink"] < 2:
								mc = markov_ohio[3]
							elif dattemp.loc[i,"Precipitation"] == 1 and dattemp.loc[i,"LanesClosedOnLink"] >= 2:
								mc = markov_ohio[4]
							elif dattemp.loc[i,"Precipitation"] in [2, 3, 5] and dattemp.loc[i,"LanesClosedOnLink"] < 2:
								mc = markov_ohio[7]
							elif dattemp.loc[i,"Precipitation"] in [2, 3, 5] and dattemp.loc[i,"LanesClosedOnLink"] >= 2:
								mc = markov_ohio[8]
							elif dattemp.loc[i,"Precipitation"] in [4, 6, 7, 8]:
								mc = markov_ohio[11]
						elif dattemp.loc[i,"IncidentDownstream"] == 1:
							if dattemp.loc[i,"Precipitation"] == 1 and dattemp.loc[i,"LanesClosedDownstream"] < 2:
								mc = markov_ohio[5]
							elif dattemp.loc[i,"Precipitation"] == 1 and dattemp.loc[i,"LanesClosedDownstream"] >= 2:
								mc = markov_ohio[6]
							elif dattemp.loc[i,"Precipitation"] in [2, 3, 5] and dattemp.loc[i,"LanesClosedDownstream"] < 2:
								mc = markov_ohio[9]
							elif dattemp.loc[i,"Precipitation"] in [2, 3, 5] and dattemp.loc[i,"LanesClosedDownstream"] >= 2:
								mc = markov_ohio[10]
							elif dattemp.loc[i,"Precipitation"] in [4, 6, 7, 8]:
								mc = markov_ohio[12]
						for j in range(4):
							stt = np.argmin(np.abs(msss - np.array(tempsp)))
							mss = msss
							mss[stt] = tempsp
							tempsp = round(np.sum(mc[stt,]*mss), 2)
						predspd.append(tempsp)
					else:
						s3 = pd.concat([dattemp.iloc[xxx-72:xxx]['Speed'].dropna(),pd.Series(predspd)]).reset_index(drop=True)
						p3 = ARIMA(s3, order=arima_order).fit()
						spd_forecast3 = p3.forecast(steps=1, alpha=0.005)
						tempsp = round(spd_forecast3.values[0], 2)
						if spd_forecastt > (spmean-10):
							tempsp = round(spd_forecast3.values[0], 2)
						else:
							if len(predspd) == 0:
								if intvl == 1:
									tempsp = spd_forecastt - (long_ts_predd.loc[tempid-1, 'speed'] - dattemp.loc[xxx-1, 'Speed'])
								else:
									tempsp = spd_forecastt
							else:
								tempsp = spd_forecastt - (predspd[0] - dattemp.loc[xxx-1, 'Speed'])
						predspd.append(tempsp)			   
	return predspd


#%%
# weather_intvl: weather update interval (minutes), e.g., 60 minutes , it can be regarded as the minimum output interval of the one-shot prediction

def oneshot(weather_intvl, startt, histdatafm, long_ts_predfm, loaded_data):
	# Access the variables
	markov_oneshot = loaded_data["markov_oneshot"]
	markov_oneshot_up = loaded_data["markov_oneshot_up"]
	centers_oneshot = loaded_data["centers_oneshot"]

	
	dattemp = histdatafm
	#intvl = int(weather_intvl / 5)
	intvl = 1
	long_ts_agg = long_ts_predfm.groupby(np.arange(len(long_ts_predfm)) // intvl).mean()
	long_ts_agg['timestamplist'] = long_ts_predfm['timestamplist'].iloc[::intvl].values
	predspd = []
	
	xxx = dattemp['Timestamp'].tolist().index(startt)
	xxx_longterm = long_ts_agg['timestamplist'].tolist().index(startt)
	msss = centers_oneshot
	tempsp = long_ts_agg['speed'].iloc[xxx_longterm]
	#for i in range(xxx, xxx + int(288 * 7),intvl):
	for i in range(xxx, xxx + int(24 * 7),intvl):
		# i = xxx
		currenttime = dattemp.iloc[i]['Timestamp']
		tempid = long_ts_agg['timestamplist'].tolist().index(currenttime)
		
		if len(predspd) == 0:
			#tempsp = dattemp['Speed'].iloc[xxx]
			predspd.append(tempsp)
		elif (dattemp.iloc[i]['Precipitation'] == 1) and any(dattemp.iloc[i-1:i-5]['Precipitation'].isin([2, 3, 5])):
			mc = markov_oneshot_up[0]
			
			for _ in range(2):
				stt = np.argmin(abs(msss - tempsp))
				mss = msss.copy()
				mss[stt] = tempsp
				tempsp = round(np.sum(mc[stt] * mss), 3)
				
			predspd.append(tempsp)
		elif (dattemp.iloc[i]['Precipitation'] == 1) and any(dattemp.iloc[i-1:i-5]['Precipitation'].isin([4, 6, 7, 8])):
			mc = markov_oneshot_up[1]
			
			for _ in range(2):
				stt = np.argmin(abs(msss - tempsp))
				mss = msss.copy()
				mss[stt] = tempsp
				tempsp = round(np.sum(mc[stt] * mss), 3)
				
			predspd.append(tempsp)
		else:
			if dattemp.iloc[i]['Precipitation'] == 1:
				tempsp = long_ts_agg['speed'].iloc[tempid]
				predspd.append(tempsp)
			elif dattemp.iloc[i]['Precipitation'] in [2, 3, 5]:
				mc = markov_oneshot[0]
				
				for _ in range(2):
					stt = np.argmin(abs(msss - tempsp))
					mss = msss.copy()
					mss[stt] = tempsp
					tempsp = round(np.sum(mc[stt] * mss), 3)
					
				predspd.append(tempsp)
			else:
				mc = markov_oneshot[1]
				
				for _ in range(2):
					stt = np.argmin(abs(msss - tempsp))
					mss = msss.copy()
					mss[stt] = tempsp
					tempsp = round(np.sum(mc[stt] * mss), 3)
					
				predspd.append(tempsp)
	return predspd

#  oneshot_input is a table combining scenario settins and long-term speed prediction values
def oneshot_new(oneshot_input, start_time, lts, loaded_data):
	# oneshot_all is used to store all predicted values across 7 days
	oneshot_all = []
	
	markov_contra = loaded_data["markov_contra"]
	markov_scenario = loaded_data["markov_ohio"]
	markov_vsl = loaded_data["markov_vsl"]
	markov_hsr = loaded_data["markov_hsr"]
	centers_contra = loaded_data["centers_contra"]

	
	msss = loaded_data["centers_ohio"]
	msss_vsl = loaded_data["centers_vsl"]
	msss_hsr = loaded_data["centers_hsr"]
	
	nOSIndex = oneshot_input['Timestamp'].tolist().index(start_time)
	nLTSIndex = lts['timestamplist'].tolist().index(start_time)
	# spdlimit refers the orignal speed limit of the road link
	spdlimit = oneshot_input['SpeedLimit'].max()
	dPrevSpeed = lts['speed'].loc[nLTSIndex - 12]
	for i in range(nOSIndex, len(oneshot_input)):
		dCurSpeed = lts['speed'].loc[nLTSIndex]
		sBranch = None
		# if speed limit changes at this time step, change long-term predicted speed accordingly
		nVsl = oneshot_input.loc[i,'vsl']
		if nVsl == spdlimit:
			nVsl = 0
		if nVsl > 0 and nVsl != spdlimit:
			dCurSpeed = dCurSpeed + (nVsl - spdlimit)
		# if all lanes closed, reduce speed to zero:
		if oneshot_input.loc[i,'LanesClosedOnLink'] >= oneshot_input.loc[i,'Lanes']:
			tempsp = 0
			sBranch = 'lanesclosed'
		# First, determine if the contraflow ever existed in the last 5 minutes. Make sure 'contraflow' is one of the column names.
		elif 'contraflow' in oneshot_input.columns and int(oneshot_input.loc[i-1, 'contraflow']) > 0:
			# If contraflow is implemented, determine which contraflow scenario it meets
			if oneshot_input.loc[i-1, 'contraflow'] == 1:
				if lts.loc[nLTSIndex-5:nLTSIndex, 'speed'].min() >= 20:
					mc = markov_contra[0]
					msss_contra = centers_contra[0]
				elif lts.loc[nLTSIndex-5:nLTSIndex, 'speed'].min() >= 10 and lts.loc[nLTSIndex-5:nLTSIndex, 'speed'].min() < 20:
					mc = markov_contra[1]
					msss_contra = centers_contra[1]
				else:
					mc = markov_contra[2]
					msss_contra = centers_contra[2]
			else:
				if lts.loc[nLTSIndex-5:nLTSIndex, 'speed'].min() >= 20:
					mc = markov_contra[3]
					msss_contra = centers_contra[3]
				elif lts.loc[nLTSIndex-5:nLTSIndex, 'speed'].min() >= 10 and lts.loc[nLTSIndex-5:nLTSIndex, 'speed'].min() < 20:
					mc = markov_contra[4]
					msss_contra = centers_contra[4]
				else:
					mc = markov_contra[5]
					msss_contra = centers_contra[5]
			tempsp = dPrevSpeed
			sBranch = 'contra'
			for j in range(2):
				stt = np.argmin(np.abs(msss_contra - np.array([tempsp])))
				mss = msss_contra.copy()
				mss[stt] = tempsp
				tempsp = round(np.sum(mc[stt,:] * mss), 2)
		else:
			# Second, determine if the vsl ever existed in the last 30 minutes
			nPrevVsl = oneshot_input.loc[i - 1, 'vsl']
			if nPrevVsl == spdlimit:
				nPrevVsl = -1
			if nVsl > 0 or nPrevVsl > 0:
				mc = markov_vsl[0]
				tempsp = dPrevSpeed
				sBranch = 'vsl'
				for j in range(2):
					stt = abs(msss_vsl - tempsp).argmin()
					mss = msss_vsl.copy()
					mss[stt] = tempsp
					tempsp = round((mc * mss).sum(), 2)
			elif i > 0 and oneshot_input.loc[i - 1, 'hsr'] > 0:
				mc = markov_hsr[0]
				tempsp = dPrevSpeed
				sBranch = 'hsr'
				for j in range(2):
					stt = abs(msss_hsr - tempsp).argmin()
					mss = msss_hsr.copy()
					mss[stt] = tempsp
					tempsp = round((mc * mss).sum(), 2)
			else:
				if oneshot_input.iloc[i]["IncidentOnLink"] == 0 and oneshot_input.iloc[i]["IncidentDownstream"] == 0:
					if (any(oneshot_input.iloc[i-1:i-4]["IncidentOnLink"] == 1) or 
						any(oneshot_input.iloc[i-1:i-4]["IncidentDownstream"] == 1) or 
						any(oneshot_input.iloc[i-1:i-4]["WorkzoneOnLink"] == 1)) and oneshot_input.iloc[i]["Precipitation"] == 1:
						tempsp = dCurSpeed
						sBranch = 'previncident_precip1'
					elif oneshot_input.iloc[i]["Precipitation"] > 2 or oneshot_input.loc[i,"LanesClosedOnLink"] >0 or oneshot_input.loc[i,"WorkzoneOnLink"] >0 or oneshot_input.loc[i,"WorkzoneDownstream"] >0:
						t = random.random()
						if t >= 0.5 :
							if oneshot_input.iloc[i]["Precipitation"] in [2,3,5]:
								mc = markov_scenario[0]
							else:
								mc = markov_scenario[1]
							tempsp = dPrevSpeed
							sBranch = 'incident_precip2'
							for j in range(2):
								stt = np.argmin(np.abs(msss - np.array(tempsp)))
								mss = msss
								mss[stt] = tempsp
								tempsp = round(np.sum(mc[stt,:] * mss), 2)
						else:
							sBranch = 'incident_precip2_coinflip'
							tempsp = dCurSpeed
					else:
						sBranch = 'no incident'
						tempsp = dCurSpeed
				else:
					t = random.random()
					if t >= 0.5 :
						if oneshot_input.loc[i,"IncidentOnLink"] == 1:
							if oneshot_input.loc[i,"Precipitation"] == 1 and oneshot_input.loc[i,"LanesClosedOnLink"] < 2:
								mc = markov_scenario[3]
							elif oneshot_input.loc[i,"Precipitation"] == 1 and oneshot_input.loc[i,"LanesClosedOnLink"] >= 2:
								mc = markov_scenario[4]
							elif oneshot_input.loc[i,"Precipitation"] in [2, 3, 5] and oneshot_input.loc[i,"LanesClosedOnLink"] < 2:
								mc = markov_scenario[7]
							elif oneshot_input.loc[i,"Precipitation"] in [2, 3, 5] and oneshot_input.loc[i,"LanesClosedOnLink"] >= 2:
								mc = markov_scenario[8]
							elif oneshot_input.loc[i,"Precipitation"] in [4, 6, 7, 8]:
								mc = markov_scenario[11]
						elif oneshot_input.loc[i,"IncidentDownstream"] == 1:
							if oneshot_input.loc[i,"Precipitation"] == 1 and oneshot_input.loc[i,"LanesClosedDownstream"] < 2:
								mc = markov_scenario[5]
							elif oneshot_input.loc[i,"Precipitation"] == 1 and oneshot_input.loc[i,"LanesClosedDownstream"] >= 2:
								mc = markov_scenario[6]
							elif oneshot_input.loc[i,"Precipitation"] in [2, 3, 5] and oneshot_input.loc[i,"LanesClosedDownstream"] < 2:
								mc = markov_scenario[9]
							elif oneshot_input.loc[i,"Precipitation"] in [2, 3, 5] and oneshot_input.loc[i,"LanesClosedDownstream"] >= 2:
								mc = markov_scenario[10]
							elif oneshot_input.loc[i,"Precipitation"] in [4, 6, 7, 8]:
								mc = markov_scenario[12]
						tempsp = dPrevSpeed
						sBranch = 'markov'
						for j in range(2):
							stt = np.argmin(np.abs(msss - np.array(tempsp)))
							mss = msss
							mss[stt] = tempsp
							tempsp = round(np.sum(mc[stt,]*mss), 2)
					else:
						sBranch = 'coinflip'
						tempsp = dCurSpeed
		oneshot_all.append(tempsp)
		dPrevSpeed = tempsp
		nLTSIndex += 12
	oneshot_all = np.array(oneshot_all)
	return oneshot_all

