import logging
import pandas as pd 
import numpy as np 

from sklearn.preprocessing import MinMaxScaler

import haversine as hs
import glob
import torch
import torch.nn as nn
from datetime import datetime, timedelta
import math



def filter_mata(data_mata,freeway_name):
	# Convert the list of freeway names to a regex pattern
	pattern = '|'.join(freeway_name)
	# Filtering the DataFrame based on whether the 'name' column contains freeway names
	data_mata = data_mata.dropna()
	filtered_mata = data_mata[data_mata['name'].str.contains(pattern)]
	filtered_mata = filtered_mata.reset_index(drop=True)
	return filtered_mata

def spatial_temporal_feature(data,hurricane,lf_time,category,lf_zone,lf_coord,filtered_df,):
	data = data.sort_values(by=['Id','Timestamp']).reset_index(drop=True)
	data = data.groupby(['Id', 'Timestamp'])[['Direction','Lanes','Speed']].mean(numeric_only=True).reset_index()
	data['time'] = pd.to_datetime(data['Timestamp'])
	data = data.groupby('Id').\
		apply(lambda x : x.set_index('time').resample('5T').mean(numeric_only=True).ffill()).\
		  reset_index()	  
	data['landfall'] = lf_time
	data['lf'] = pd.to_datetime(data['landfall'])
	data['t_to_lf'] = data['time'] - data['lf']
	data['t_to_lf'] = pd.to_timedelta(data['t_to_lf']).dt.total_seconds()/3600
	data = pd.merge(data,filtered_df[['imrcpid','lat','lon']],left_on = 'Id',right_on=['imrcpid'])
	data = data.drop(['imrcpid', 'landfall','lf'], axis=1)
	data['hour'] = data['time'].dt.hour
	data['category'] = category
	data['lf_zone'] = lf_zone
	# add distance to landfall column
	filtered_df['dis_to_lf'] = 0
	for i in range(len(filtered_df)):
		gps_link = (filtered_df.loc[i,'lat'],filtered_df.loc[i,'lon'])
		filtered_df.loc[i,'dis_to_lf'] = hs.haversine(gps_link,lf_coord) 
	filtered_df = filtered_df.rename(columns={'imrcpid':'Id'})
	data = pd.merge(data,filtered_df[['Id','dis_to_lf']],on = 'Id',how='left')
	data['hurricane'] = hurricane
	data['Id_hur'] = data['Id'] + data['hurricane']
	return data



def extract_csvfile(folder_path,file_pattern_prefix,hurricane,category,lf_time,lf_zone,lf_coord):
	file_list = glob.glob(folder_path + '/' + file_pattern_prefix + '*.csv')  # Adjust the file extension as needed
	file_list.sort()  # Ensure the files are sorted
	# Separate the file list into two parts
	past_7d_filename = file_list[:7]
	next_7d_filename = file_list[7:]
	next_8d_filename = file_list[6:]

	# Read the first 7 files
	combined_past7d = [pd.read_csv(file) for file in past_7d_filename]
	combined_past7d = pd.concat(combined_past7d, ignore_index=True)
	print('past 7 done')
	# Read the last 7 files 
	combined_next7d = [pd.read_csv(file) for file in next_7d_filename]
	combined_next7d = pd.concat(combined_next7d, ignore_index=True)
	print('next 7 done')
	# Read the last 8 files 
	combined_next8d = [pd.read_csv(file) for file in next_8d_filename]
	combined_next8d = pd.concat(combined_next8d, ignore_index=True)
	print('next 8 done')

	# lf_time = '2021-08-29 14:00:00'
	# category = 4
	# lf_zone = 1
	# lf_coord = (29.1,-90.2)
	# hurricane = 'hurricane'

	# combine speed data for the past 7 days
	# # Create a list of file paths using glob
	# file_paths = glob.glob('test_data_past7/LA_speeds_*.csv')
	# # Read and concatenate DataFrames using list comprehension
	# data_frames = [pd.read_csv(file_path) for file_path in file_paths]
	# combined_past7d = pd.concat(data_frames, ignore_index=True)
	combined_past7d['Time'] = pd.to_datetime(combined_past7d['Timestamp'])
	combined_past7d = combined_past7d.groupby('Id').apply(lambda x : x.set_index('Time').resample('5T').mean(numeric_only=True).ffill()).reset_index()  
	combined_past7d = combined_past7d[['Id','Time','Speed']]
	combined_past7d['hurricane'] = hurricane
	combined_past7d['Id_hur'] = combined_past7d['Id']+combined_past7d['hurricane']

	# # check original data format
	# # df = pd.read_csv('LA_speeds_20210826.csv')

	# # Create a list of file paths using glob
	# file_paths = glob.glob('test_data/LA_speeds_*.csv')

	# # Read and concatenate DataFrames using list comprehension
	# data_frames = [pd.read_csv(file_path) for file_path in file_paths]
	# combined_7d = pd.concat(data_frames, ignore_index=True)
	combined_next7d = combined_next7d[['Timestamp','Id','Direction','DayOfWeek','Lanes','Speed']]
	combined_next7d['hurricane'] = hurricane

	combined_next8d = combined_next8d[['Timestamp','Id','Direction','DayOfWeek','Lanes','Speed']]
	combined_next8d['hurricane'] = hurricane

	
	# add features
	# lf_time = '2021-08-29 14:00:00'
	# category = 4
	# lf_zone = 1
	# lf_coord = (29.1,-90.2)
	return combined_past7d,combined_next7d,combined_next8d
	# return 1==1

def oneshot_annotate(data_w_feature_7d,combined_past7d,lf_time, oMeansAndStds, sHur):
	time_string = lf_time
	# Convert the time string to a datetime object
	time_dt = datetime.strptime(time_string, '%Y-%m-%d %H:%M:%S')
	# Define the start time of the first 6-hour period
	start_time = time_dt.replace(hour=0, minute=0, second=0)
	# Calculate the time difference in hours
	time_diff = (time_dt - start_time).total_seconds() / 3600
	# Determine the 6-hour period
	period = int(time_diff // 6) + 1


	
	#I10 id list
	linkid = data_w_feature_7d['Id_hur'].unique()
	data_6hr_7d_all = None
	for i in range(len(linkid)):
		try:
			oMeanAndStd = oMeansAndStds[linkid[i]]
			# i = 0
			df_temp = data_w_feature_7d[data_w_feature_7d['Id_hur']==linkid[i]]
			#df_temp_past = combined_past7d[combined_past7d['Id_hur']==linkid[i]]
			spd_mean_past7 = oMeanAndStd[0]
			spd_std_past7 = oMeanAndStd[1]
# =============================================================================
# 			spd_mean_past7 = df_temp_past['Speed'].mean()
# 			spd_std_past7 = df_temp_past['Speed'].std()
# =============================================================================
			dTotal = 0.0
			nCount = 0
			dMeans = []
			oCutoff = start_time - timedelta(hours=66) # 3 days back plus 6 hours
			nLimit = len(df_temp) - 1
			for nIndex in range(0, len(df_temp)):
				oRec = df_temp.iloc[nIndex]
				if oRec.time > oCutoff or nIndex == nLimit:
					if nCount > 0:
						dMeans.append(dTotal / nCount)
					else:
						dMeans.append(math.nan)
					nCount = 0
					dTotal = 0.0
					oCutoff = oCutoff + timedelta(hours=6)
				else:
					dTotal += oRec.Speed
					nCount += 1
			if not len(dMeans) == 28:
				continue
			bSkip = False
			for nIndex in range(0, 28):
				dVal = dMeans[nIndex]
				if math.isnan(dVal):
					if nIndex == 0:
						if math.isnan(dMeans[1]):
							bSkip = True
							break
						dMeans[0] = dMeans[1]
					elif nIndex == 27:
						if math.isnan(dMeans[26]):
							bSkip = True
							break
						dMeans[27] = dMeans[26]
					else:
						dVal = (dMeans[nIndex - 1] + dMeans[nIndex + 1]) / 2
						if math.isnan(dVal):
							bSkip = True
							break
						dMeans[nIndex] = dVal
			if bSkip:
				continue
			spd_mean_6hr = np.array(dMeans)
			#spd_mean_6hr = np.array(df_temp['Speed']).reshape(7*4,int(288/4)).mean(axis=1)
			if spd_mean_past7>0:
				# con_index = np.where(spd_mean_seg<(spd_mean-spd_std))[0]
				data_6hr_7d = df_temp[['Id','Id_hur','Direction','Lanes','lat','lon']][0:28].reset_index(drop=True)
				# label_out['congested'] = 0
				# label_out.loc[label_out.index.isin(list(con_index)),'congested'] = 1
				data_6hr_7d['t_to_lf'] = np.arange(0, 7*4)
				data_6hr_7d['t_to_lf'] = data_6hr_7d['t_to_lf']-(11 + period)
				# label_out = label_out[['Id','congested','t_to_lf','Direction','Lanes','lat','lon',]]
				# gps_link = (label_out['lat'][0],label_out['lon'][0])
				data_6hr_7d['dis_to_lf'] = df_temp['dis_to_lf'].iloc[0] 
				data_6hr_7d['lf_zone'] = df_temp['lf_zone'].iloc[0] 
				data_6hr_7d['category'] = df_temp['category'].iloc[0] 
				data_6hr_7d['timeofday'] = [1,2,3,4]*7
				data_6hr_7d['spd_mean_past7'] = spd_mean_past7
				data_6hr_7d['spd_std_past7'] = spd_std_past7
				data_6hr_7d['spd_mean_current'] = spd_mean_6hr
				if data_6hr_7d_all is None:
					data_6hr_7d_all = data_6hr_7d
				else:
					data_6hr_7d_all = pd.concat([data_6hr_7d_all, data_6hr_7d], ignore_index=True)
			if i%100 == 99:
				dPercent = round(i / len(linkid), 2) * 100
				logging.info('Oneshot annotate {:.0f}% complete for storm {}!@#'.format(dPercent, sHur))

			if i == len(linkid)-1:
				logging.info('Oneshot annotate 100% complete for storm {}!@#'.format(sHur))

		except BaseException:
			logging.exception('')
			
	data_6hr_7d_all['spd_ratio'] = data_6hr_7d_all['spd_mean_current']/data_6hr_7d_all['spd_mean_past7']
	data_6hr_7d_all['congested'] = 0
	data_6hr_7d_all.loc[((data_6hr_7d_all['spd_ratio']>=0.5)&(data_6hr_7d_all['spd_ratio']<0.75)),'congested'] = 1
	data_6hr_7d_all.loc[(data_6hr_7d_all['spd_ratio']<0.5),'congested'] = 2
	data_6hr_7d_all = data_6hr_7d_all[['Id','Id_hur','t_to_lf','Direction','Lanes','lat','lon','dis_to_lf','timeofday','spd_mean_past7','spd_std_past7','category','lf_zone','congested']]
	return data_6hr_7d_all



def create_dummy_oneshot(data_w_feature_8d,data_6hr_7d_all):
	nNumLanes = int(data_w_feature_8d['Lanes'].max())
	dummy_maxlen = int(max(5, nNumLanes))
	dummy_maxlat,dummy_minlat = data_w_feature_8d['lat'].max(), data_w_feature_8d['lat'].min()
	dummy_maxlon,dummy_minlon = data_w_feature_8d['lon'].max(), data_w_feature_8d['lon'].min()
	dummy_maxdistolf,dummy_mindistolf = data_w_feature_8d['dis_to_lf'].max(), data_w_feature_8d['dis_to_lf'].min()
	dummy_max_spd_mean,dummy_min_spd_mean = data_6hr_7d_all['spd_mean_past7'].max(), data_6hr_7d_all['spd_mean_past7'].min()
	dummy_max_spd_std,dummy_min_spd_std = data_6hr_7d_all['spd_std_past7'].max(), data_6hr_7d_all['spd_std_past7'].min()
	dummy_oneshot = data_6hr_7d_all.loc[range(dummy_maxlen),['Id', 't_to_lf', 'Direction', 'Lanes', 'lat', 'lon','dis_to_lf', 'timeofday', 'spd_mean_past7', 'spd_std_past7', 'category', 'lf_zone']]

	for i in range(nNumLanes):
		dummy_oneshot.loc[i, 'Lanes'] = i + 1
	for i in range(4):
		dummy_oneshot.loc[i, 'Direction'] = i + 1
	for i in range(2):
		dummy_oneshot.loc[i, 'lf_zone'] = i
	for i in range(5):
		dummy_oneshot.loc[i, 'category'] = i + 1
	for i in range(4):
		dummy_oneshot.loc[i, 'timeofday'] = i + 1
	dummy_oneshot.loc[0, 't_to_lf'] = -15
	dummy_oneshot.loc[1, 't_to_lf'] = 15
	dummy_oneshot.loc[0, 'lat'] = dummy_minlat
	dummy_oneshot.loc[1, 'lat'] = dummy_maxlat
	dummy_oneshot.loc[0, 'lon'] = dummy_minlon
	dummy_oneshot.loc[1, 'lon'] = dummy_maxlon
	dummy_oneshot.loc[0, 'dis_to_lf'] = dummy_mindistolf
	dummy_oneshot.loc[1, 'dis_to_lf'] = dummy_maxdistolf
	dummy_oneshot.loc[0, 'spd_mean_past7'] = dummy_min_spd_mean
	dummy_oneshot.loc[1, 'spd_mean_past7'] = dummy_max_spd_mean
	dummy_oneshot.loc[0, 'spd_std_past7'] = dummy_min_spd_std
	dummy_oneshot.loc[1, 'spd_std_past7'] = dummy_max_spd_std
	for i in range(3):
		dummy_oneshot.loc[i, 'congested'] = i
	dummy_oneshot = dummy_oneshot.fillna(1)
	
# =============================================================================
# 	dummy_oneshot.loc[range(dummy_maxlen),'Lanes'] = range(1, dummy_maxlen+1)
# 	dummy_oneshot.loc[range(4),'Direction'] = range(1, 5)
# 	dummy_oneshot.loc[range(2),'lf_zone'] = range(2)
# 	dummy_oneshot.loc[range(4),'category'] = range(1, 5)
# 	dummy_oneshot.loc[range(4),'timeofday'] = range(1, 5)
# 	dummy_oneshot.loc[range(2),'t_to_lf'] = [-15,15]
# 	dummy_oneshot.loc[range(2),'lat'] = [dummy_minlat,dummy_maxlat]
# 	dummy_oneshot.loc[range(2),'lon'] = [dummy_minlon,dummy_maxlon] 
# 	dummy_oneshot.loc[range(2),'dis_to_lf'] = [dummy_mindistolf,dummy_maxdistolf]
# 	dummy_oneshot.loc[range(2),'spd_mean_past7'] = [dummy_min_spd_mean,dummy_max_spd_mean]
# 	dummy_oneshot.loc[range(2),'spd_std_past7'] = [dummy_min_spd_std,dummy_max_spd_std]
# 	dummy_oneshot.loc[range(2),'lf_zone'] = [0,1]
# 	dummy_oneshot.loc[range(3),'congested'] = [0,1,2]
# =============================================================================
	
	return dummy_oneshot

def create_dummy_online(data_w_feature_8d,data_6hr_7d_all):
	nNumLanes = int(data_w_feature_8d['Lanes'].max())
	dummy_maxlen = int(max(5, nNumLanes))
	dummy_maxlat,dummy_minlat = data_w_feature_8d['lat'].max(), data_w_feature_8d['lat'].min()
	dummy_maxlon,dummy_minlon = data_w_feature_8d['lon'].max(), data_w_feature_8d['lon'].min()
	dummy_maxdistolf,dummy_mindistolf = data_w_feature_8d['dis_to_lf'].max(), data_w_feature_8d['dis_to_lf'].min()
	dummy_max_spd,dummy_min_spd = data_w_feature_8d['Speed'].max(), data_w_feature_8d['Speed'].min()


	dummy_online = data_w_feature_8d.loc[range(dummy_maxlen),['Id', 't_to_lf', 'Direction', 'Lanes', 'lat', 'lon',
		   'dis_to_lf', 'category', 'lf_zone','Speed','hour']]
	for i in range(nNumLanes):
		dummy_online.loc[i, 'Lanes'] = i + 1
	for i in range(4):
		dummy_online.loc[i, 'Direction'] = i + 1
	for i in range(2):
		dummy_online.loc[i, 'lf_zone'] = i
	for i in range(5):
		dummy_online.loc[i, 'category'] = i + 1
	
	dummy_online.loc[0,'hour'] = 0
	dummy_online.loc[1,'hour'] = 23
	dummy_online.loc[0, 't_to_lf'] = -120
	dummy_online.loc[1, 't_to_lf'] = 96
	dummy_online.loc[0, 'lat'] = dummy_minlat
	dummy_online.loc[1, 'lat'] = dummy_maxlat
	dummy_online.loc[0, 'lon'] = dummy_minlon
	dummy_online.loc[1, 'lon'] = dummy_maxlon
	dummy_online.loc[0, 'dis_to_lf'] = dummy_mindistolf
	dummy_online.loc[1, 'dis_to_lf'] = dummy_maxdistolf
	dummy_online.loc[0,'Speed'] = dummy_min_spd
	dummy_online.loc[1,'Speed'] = dummy_max_spd
	
# =============================================================================
# 	dummy_online.loc[range(dummy_maxlen),'Lanes'] = range(1, dummy_maxlen+1)
# 	dummy_online.loc[range(4),'Direction'] = range(1, 5)
# 	dummy_online.loc[range(2),'lf_zone'] = range(2)
# 	dummy_online.loc[range(4),'category'] = range(1, 5)
# 	dummy_online.loc[range(2),'hour'] = [0,23]
# 	dummy_online.loc[range(2),'t_to_lf'] = [-120,96]
# 	dummy_online.loc[range(2),'lat'] = [dummy_minlat,dummy_maxlat]
# 	dummy_online.loc[range(2),'lon'] = [dummy_minlon,dummy_maxlon] 
# 	dummy_online.loc[range(2),'dis_to_lf'] = [dummy_mindistolf,dummy_maxdistolf]
# 	dummy_online.loc[range(2),'lf_zone'] = [0,1]
# 	dummy_online.loc[range(2),'Speed'] = [dummy_min_spd,dummy_max_spd]
# =============================================================================
	return dummy_online

def data_normalization_oneshot(df,dummy):
		size_dummy = len(dummy)
		# categorical features to be onverted to One Hot Encoding
		df_onehot = df.copy()
		df_onehot = df_onehot[['Id','category','timeofday','Lanes', 't_to_lf','lat', 'lon', 'dis_to_lf','spd_mean_past7','spd_std_past7','Direction','lf_zone']]
		df_dummy = dummy[['Id','category','timeofday','Lanes', 't_to_lf','lat', 'lon', 'dis_to_lf','spd_mean_past7','spd_std_past7','Direction','lf_zone']]
		df_onehot = pd.concat([df_dummy,df_onehot])
		df_onehot = df_onehot.reset_index(drop=True)
		scaler = MinMaxScaler(feature_range=(-1, 1)) 
		df_onehot['t_to_lf'] = scaler.fit_transform(df_onehot[['t_to_lf']])
		scaler = MinMaxScaler(feature_range=(0, 1)) 
		df_onehot['lat'] = scaler.fit_transform(df_onehot[['lat']])
		df_onehot['lon'] = scaler.fit_transform(df_onehot[['lon']])
		df_onehot['dis_to_lf'] = scaler.fit_transform(df_onehot[['dis_to_lf']])
		df_onehot['spd_mean_past7'] = scaler.fit_transform(df_onehot[['spd_mean_past7']])
		df_onehot['spd_std_past7'] = scaler.fit_transform(df_onehot[['spd_std_past7']])
		categ = ['Direction','lf_zone']
		df_onehot = pd.get_dummies(df_onehot, prefix_sep="_", columns=categ, dtype=float)
		df_onehot = df_onehot.drop(columns=['Id'])
		array_input = df_onehot.to_numpy()
		array_input = array_input[size_dummy:] # remove the dummy rows
		#array_labels = df['congested'].values.reshape(-1,1) 
		#return array_input,array_labels,df_onehot.columns
		return array_input,None,df_onehot.columns
	
def data_normalization_online(df,dummy):
		# categorical features to be onverted to One Hot Encoding
		size_dummy = len(dummy)
		df_onehot = df.copy()
		df_onehot = df_onehot[['Id','category','hour','Lanes', 't_to_lf','lat', 'lon', 'dis_to_lf','Direction','lf_zone','Speed']]
		df_dummy = dummy[['Id','category','hour','Lanes', 't_to_lf','lat', 'lon', 'dis_to_lf','Direction','lf_zone','Speed']]
		df_onehot = pd.concat([df_dummy,df_onehot])
		df_onehot = df_onehot.reset_index(drop=True)
		scaler = MinMaxScaler(feature_range=(-1, 1)) 
		df_onehot['t_to_lf'] = scaler.fit_transform(df_onehot[['t_to_lf']])
		scaler = MinMaxScaler(feature_range=(0, 1)) 
		df_onehot['lat'] = scaler.fit_transform(df_onehot[['lat']])
		df_onehot['lon'] = scaler.fit_transform(df_onehot[['lon']])
		df_onehot['dis_to_lf'] = scaler.fit_transform(df_onehot[['dis_to_lf']])
		df_onehot['Speed'] = scaler.fit_transform(df_onehot[['Speed']])
		# df_onehot['spd_mean_past7'] = scaler.fit_transform(df[['spd_mean_past7']])
		# df_onehot['spd_std_past7'] = scaler.fit_transform(df[['spd_std_past7']])
		categ = ['Direction','lf_zone']
		df_onehot = pd.get_dummies(df_onehot, prefix_sep="_", columns=categ, dtype=float)
		df_onehot = df_onehot.drop(columns=['Id'])
		array_input = df_onehot.to_numpy()
		array_input = array_input[size_dummy:]
		df_out = df[['Id','Speed']]
		dummy_out = dummy[['Id','Speed']]
		df_out = pd.concat([df_out,dummy_out]) # remove the dummy rows
		df_out = df_out.reset_index(drop=True)
		array_labels = scaler.fit_transform(df_out['Speed'].to_numpy().reshape(-1, 1))
		array_labels = array_labels[size_dummy:] # remove the dummy rows
		return array_input,array_labels

	
def initialize_oneshot(input_size, path_oneshot):
	class MultiClassCongestion(nn.Module):
		def __init__(self, input_size, output_size):
			super(MultiClassCongestion, self).__init__()
			self.fc1 = nn.Linear(input_size, 64)
			self.fc2 = nn.Linear(64, 32)
			self.fc3 = nn.Linear(32, output_size)
			self.relu = nn.ReLU()
			self.softmax = nn.Softmax(dim=1)

		def forward(self, x):
			out = self.fc1(x)
			out = self.relu(out)
			out = self.fc2(out)
			out = self.relu(out)
			out = self.fc3(out)
			out = self.softmax(out)
			return out
	model_oneshot = MultiClassCongestion(input_size,3) # initialize the model with 20 inputs features and 3 output labels
	model_oneshot.load_state_dict(torch.load(path_oneshot)) # load the trained model from file
	model_oneshot.eval() # set the model in evaluation mode
	return model_oneshot


def initialize_online(input_size, path_online):
	class LSTM(nn.Module):
		def __init__(self, input_size, hidden_size, num_layers):
			super(LSTM, self).__init__()
			self.hidden_size = hidden_size
			self.num_layers = num_layers
			self.lstm = nn.LSTM(input_size, hidden_size, num_layers, batch_first=True)
			self.fc = nn.Linear(hidden_size, 1)

		def forward(self, x):
			# initialize hidden state and cell state
			h0 = torch.zeros(self.num_layers, x.size(0), self.hidden_size)
			c0 = torch.zeros(self.num_layers, x.size(0), self.hidden_size)

			# forward propagate LSTM
			out, _ = self.lstm(x, (h0, c0))

			# decode the hidden state of the last time step
			out = self.fc(out[:, -1, :])
			return out

	model_online = LSTM(input_size,64,2) # initialize the model with 21 input features,2 hidden layers and each hidden layer with 64 nodes
	model_online.load_state_dict(torch.load(path_online))
	model_online.eval()  # set the model in evaluation mode
	return model_online
	
	
def oneshot_predict(input_oneshot,dummy_oneshot,input_past7speed,model_oneshot):
	norm_input, _, _ = data_normalization_oneshot(input_oneshot,dummy_oneshot)
	
	inputs_tensor = torch.tensor(norm_input, dtype=torch.float32)
	outputs = model_oneshot(inputs_tensor)
	predicted_class = torch.argmax(outputs, dim=1) # predict congestion labels for each 6-hour window across 7 days, giving 28 output labels for a link
	predicted_class = predicted_class.tolist()

	df_pred = pd.DataFrame({'prediction':predicted_class})
	df_pred = df_pred.reset_index(drop=True)
	df_pred['ratio'] = 1
	df_pred.loc[df_pred['prediction']==1,'ratio'] = np.random.uniform(low = 0.5, high = 0.8, size=len(df_pred[df_pred['prediction']==1]))
	df_pred.loc[df_pred['prediction']==2,'ratio'] = np.random.uniform(low = 0.1, high = 0.5, size=len(df_pred[df_pred['prediction']==2]))
	df_congestion = pd.DataFrame(np.repeat(df_pred.values, 72, axis=0))
	df_next7_pred = pd.DataFrame({'Speed':input_past7speed['Speed'],'congestion':df_congestion.loc[:,0],'ratio': df_congestion.loc[:,1]})
	df_next7_pred['Speed'] = df_next7_pred['Speed']*df_next7_pred['ratio']
	
	
	return df_next7_pred['Speed'].values


def online_predict(start_time,input_online,dummy_online,model_online):
	single_link_input = input_online
	strt_index = single_link_input[single_link_input['time']==start_time].index.values[0]
	single_link_input = single_link_input[strt_index-288:strt_index:3]

	inputs,_ = data_normalization_online(single_link_input,dummy_online) # obtain normalized input
	inputs = inputs.reshape(1,96,inputs.shape[-1]) # reshape the large 2d array to a 3d array, 1st dimension refers to link, 2nd dimension refers to timestamp, 3rd dimenstion refers to features
	tensor_x = torch.FloatTensor(inputs)
	test_loader = torch.utils.data.DataLoader(tensor_x, batch_size=10, shuffle=False)
	outputs = model_online(next(iter(test_loader)))*90 
	return outputs
