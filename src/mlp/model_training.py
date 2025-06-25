
import pandas as pd 
import numpy as np 
from sklearn.preprocessing import MinMaxScaler
import torch.nn as nn
from torch.utils.data import ConcatDataset, Dataset, DataLoader
import torch
import time

import logging
import random


def train_oneshot(df,dummy):
	oneshot_input = df
	# remove odd data
	oddlink =  oneshot_input[oneshot_input['Lanes']==-1]['Id'].unique()	 
	oneshot_input = oneshot_input[~oneshot_input['Id'].isin(oddlink)]
	oneshot_input = oneshot_input.drop_duplicates()  
	# balance medium congestion data samples
	data_congest = oneshot_input[oneshot_input['congested']==1]
	dup_num = round(len(oneshot_input[oneshot_input['congested']==0])/len(oneshot_input[oneshot_input['congested']==1]))
	data_congest_dup = pd.concat([data_congest]*(dup_num-1), ignore_index=True)
	oneshot_input = pd.concat([oneshot_input,data_congest_dup])
	oneshot_input = oneshot_input.reset_index(drop=True)
	# balance heavy congestion data samples
	data_congest = oneshot_input[oneshot_input['congested']==2]
	dup_num = round(len(oneshot_input[oneshot_input['congested']==0])/len(oneshot_input[oneshot_input['congested']==2]))
	data_congest_dup = pd.concat([data_congest]*(dup_num-1), ignore_index=True)
	oneshot_input = pd.concat([oneshot_input,data_congest_dup])
	oneshot_input = oneshot_input.reset_index(drop=True)
	
	# #%% 2. data normalization
	def data_normalization(df,dummy):
		size_dummy = len(dummy)
		# categorical features to be onverted to One Hot Encoding
		df_onehot = df.copy()
		df_onehot = df_onehot[['Id','category','timeofday','Lanes', 't_to_lf','lat', 'lon', 'dis_to_lf','spd_mean_past7','spd_std_past7','Direction','lf_zone' ]]
		df_dummy = dummy[['Id','category','timeofday','Lanes', 't_to_lf','lat', 'lon', 'dis_to_lf','spd_mean_past7','spd_std_past7','Direction','lf_zone' ]]
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
		array_input = df_onehot.values
		array_input = array_input[size_dummy:] # remove the dummy rows
		array_labels = df['congested'].values.reshape(-1,1) 
		return array_input,array_labels,df_onehot.columns

	tic = time.perf_counter()
	norm_x, norm_y, input_col = data_normalization(oneshot_input,dummy)
	toc = time.perf_counter()
	
	logging.info(f'Data normalization finished in : {(toc-tic):.2f} sec')
	
	
	#%% 3. batch processing

	# split the train, validate, and test dataset
	# shuffle the normalized data
	def unison_shuffled_copies(a, b):
		assert len(a) == len(b)
		np.random.seed(0)
		p = np.random.permutation(len(a))
		return a[p], b[p]

	cl_data_out1, target_out1 = unison_shuffled_copies(norm_x,norm_y)

	size_train = int(len(cl_data_out1)*0.6)
	size_val = int(len(cl_data_out1)*0.2)
	size_test = int(len(cl_data_out1)*0.2)

	cl_x_train, cl_y_train = cl_data_out1[:size_train], target_out1[:size_train]
	cl_x_val, cl_y_val = cl_data_out1[size_train:size_train+size_val], target_out1[size_train:size_train+size_val]
	cl_x_test, cl_y_test = cl_data_out1[size_train+size_val:len(cl_data_out1)], target_out1[size_train+size_val:len(cl_data_out1)]

	cl_y_train = cl_y_train.flatten()
	cl_y_val = cl_y_val.flatten()
	cl_y_test = cl_y_test.flatten()
	
	# Define the dataset class
	class DataTensor(Dataset):
		def __init__(self, data, labels):
			self.data = torch.tensor(data, dtype=torch.float32)
			self.labels = torch.tensor(labels, dtype=torch.long)
		def __len__(self):
			return len(self.data)
		def __getitem__(self, index):
			return self.data[index], self.labels[index]

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

	# convert data from array to tensors
	train_dataset = DataTensor(cl_x_train, cl_y_train)
	val_dataset = DataTensor(cl_x_val, cl_y_val)
	test_dataset = DataTensor(cl_x_test, cl_y_test)

	# Partition data into batches
	train_loader = DataLoader(train_dataset, batch_size=32, shuffle=True)
	val_loader = DataLoader(val_dataset, batch_size=32, shuffle=False)
	test_loader = DataLoader(test_dataset, batch_size=32, shuffle=False)

	# Define the model and optimizer
	input_size = cl_x_train.shape[1]
	output_size = 3
	
	logging.info(f"Input layer size: {input_size}")
	logging.info(f"Output layer size: {output_size}")
	
	model = MultiClassCongestion(input_size, output_size)
	optimizer = torch.optim.Adam(model.parameters(), lr=0.001)
	num_epochs = 100
	
	# Train the model
	train_losses = []
	val_losses = []
	train_accuracies = []
	val_accuracies = []
	for epoch in range(num_epochs):

		tic_ep = time.perf_counter()
		train_loss = 0.0
		val_loss = 0.0
		train_accuracy = 0.0
		val_accuracy = 0.0
		total = 0
		correct = 0
		nSize = len(train_loader)
		dPercent = 0.1
		for x, (inputs, labels) in enumerate(train_loader):
			if x / nSize > dPercent:
				logging.info('{:.0f}% complete for epoch {} of oneshot training!@#'.format(dPercent * 100, epoch +1))
				dPercent += .1
			optimizer.zero_grad()
			outputs = model(inputs)
			loss = nn.CrossEntropyLoss()(outputs, labels)
			loss.backward()
			optimizer.step()
			_, predicted = torch.max(outputs.data, 1)
			total += labels.size(0)
			correct += (predicted == labels).sum().item()
			train_loss += loss.item()
			# accuracy = 100 * correct / total
			# train_accuracy += accuracy


		train_loss /= len(train_loader)
		train_losses.append(train_loss)
		train_accuracy =  correct / total
		train_accuracies.append(train_accuracy)


		# Evaluate the model on the validation set
		model.eval()

		with torch.no_grad():
			total = 0
			correct = 0
			for inputs, labels in val_loader:
				outputs = model(inputs)
				_, predicted = torch.max(outputs.data, 1)
				total += labels.size(0)
				correct += (predicted == labels).sum().item()
				loss = nn.CrossEntropyLoss()(outputs, labels)
				val_loss += loss.item()
				# accuracy = 100 * correct / total
				# val_accuracy += accuracy
			val_loss /= len(val_loader)
			val_losses.append(val_loss)	
			val_accuracy =   correct / total
			val_accuracies.append(val_accuracy)


		toc_ep = time.perf_counter()
		epoch_time = toc_ep-tic_ep  
		logging.info(f"Epoch {epoch+1}/{num_epochs}: Train Loss: {train_loss:.4f}, Val Loss: {val_loss:.4f}, Train Acc: {train_accuracy:.4f}, Epoch {epoch + 1}, Validation Acc: {val_accuracy:.4f}, Time: {epoch_time:.2f} sec") 
		logging.info(f"Epoch {epoch+1}/{num_epochs}: Train Loss: {train_loss:.4f}, Train Acc: {train_accuracy:.4f}, Epoch {epoch + 1}, Time: {epoch_time:.2f} sec!@#") 

	return model

def train_online(df_label, df_speed, dummy, horizon):
	# hurricane speed data balancing
	# len(freeway_name)
	def data_balance_one_hurricane(df_label,df_speed):
		# 7-day labeled data (7*4 = 28)
		label_data = df_label
		link_congestion = df_label.groupby('Id_hur')['congested'].sum()
		congested_id = link_congestion[link_congestion>0].index
		# 7-day speed data (7*288 = 2016)
		speed_data = df_speed
		# speed_data = pd.merge(speed_data,label_data[['Id','spd_mean_past7']],on='Id',how='left')
		oddlink =  speed_data[speed_data['Lanes']==-1]['Id_hur'].unique()
		speed_data = speed_data[~speed_data['Id_hur'].isin(oddlink)]
		speed_data = speed_data.drop_duplicates()
		link_datacnt = speed_data.groupby('Id_hur').size()
		short_id = link_datacnt[link_datacnt!=2304].index
		speed_data = speed_data[~speed_data['Id_hur'].isin(short_id)]
		# duplicate congested links
		# uncongest_id = link_congestion[link_congestion==0].index
		# dup_num = round(len(uncongest_id)/len(congested_id))
		# data_imp_dup = pd.concat([speed_data[speed_data['Id'].isin(congested_id)]]*1, ignore_index=True)
		# speed_data = pd.concat([speed_data,data_imp_dup])
		speed_data = speed_data.reset_index(drop=True)
		
		id_con = label_data.groupby('Id_hur')['congested'].sum()
		id_uncon = label_data.groupby('Id_hur')['congested'].sum()
		id_con = id_con[id_con>0]
		id_uncon = id_uncon[id_uncon==0]
		len_id_uncon = len(id_uncon)
		sample_id_uncon = random.sample(list(id_uncon.index), int(len_id_uncon*0.5))
		dup_num = round(len(id_uncon)/len(id_con))
		
		data_imp_dup = pd.concat([speed_data[speed_data['Id_hur'].isin(congested_id)]]*(dup_num-1), ignore_index=True)
		speed_data = pd.concat([speed_data[speed_data['Id_hur'].isin(sample_id_uncon)],data_imp_dup])

		# speed_data = speed_data[(speed_data['Id_hur'].isin(id_con)) | (speed_data['Id_hur'].isin(sample_id_uncon))]
		# speed_data['hurricane'] = hurricane
		return speed_data

	speed_data_all = data_balance_one_hurricane(df_label,df_speed)

	# data normalization

	# #%% 2. data normalization
	def data_normalization_online(df,dummy):
		# categorical features to be onverted to One Hot Encoding
		size_dummy = len(dummy)
		df_onehot = df.copy()
		df_onehot = df_onehot[['Id','category','hour','Lanes', 't_to_lf','lat', 'lon', 'dis_to_lf','Direction','lf_zone','Speed']]
		df_dummy = dummy[['Id','category','hour','Lanes', 't_to_lf','lat', 'lon', 'dis_to_lf','Direction','lf_zone','Speed']]
		df_onehot = pd.concat([dummy,df_onehot])
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
		array_input = df_onehot.values
		array_input = array_input[size_dummy:]
		df_out = df[['Id','Speed']]
		dummy_out = dummy[['Id','Speed']]
		df_out = pd.concat([df_out,dummy_out]) # remove the dummy rows
		df_out = df_out.reset_index(drop=True)
		array_labels = scaler.fit_transform(df_out['Speed'].to_numpy().reshape(-1, 1))
		array_labels = array_labels[size_dummy:] # remove the dummy rows
		return array_input,array_labels


	tic = time.perf_counter()
	data_out_raw, target_out_raw = data_normalization_online(speed_data_all,dummy)
	toc = time.perf_counter()
	logging.info(f'Data normalization finished in : {(toc-tic):.2f} sec')

	# reshape data
	numoflink = int(len(data_out_raw)/576)
	lenofseq = 576
	numcol = len(data_out_raw[0])
	data_out = data_out_raw.reshape(numoflink,lenofseq,numcol)
	target_out = target_out_raw.reshape(numoflink,lenofseq,1)
	# data_out_withid = data_out_raw_withid.reshape(numoflink,lenofseq,numcol+2)

	def unison_shuffled_copies(a, b):
		assert len(a) == len(b)
		p = np.random.permutation(len(a))
		return a[p], b[p]
	data_out1, target_out1 = unison_shuffled_copies(data_out,target_out)

	# define data split fraction and train/validate/test set size
	num_train_samples = int(0.6 * len(data_out1))
	num_val_samples = int(0.2 * len(data_out1))
	num_test_samples = len(data_out1) - num_train_samples - num_val_samples

	
	sampling_step = 2
	sampling_rate = 3
	train_window = int((12/sampling_rate)*24)
	batch_size = 16

	# create time sequence samples from normalized input data
	# create timeseries data
	class TimeseriesDataset(torch.utils.data.Dataset):
		def __init__(self, X: torch.tensor, y: torch.tensor, seq_len: int = 1, window: int = 1):
			self.X = X
			self.y = y
			self.seq_len = seq_len
			self.window = window

		def __len__(self) -> int:
			return self.X.__len__() - (self.seq_len + (self.window - 1))

		def __getitem__(self, index):
			return (self.X[index:index + self.seq_len], self.y[index + self.seq_len + (self.window - 1)])


	# training dataset generation
	logging.info('Data split started')
	tic = time.perf_counter()
	oTrainLoaders = []
	for i in range(num_train_samples):
		tensor_x = torch.FloatTensor(data_out1[i][0:2016:sampling_rate])
		tensor_y = torch.FloatTensor(target_out1[i][0:2016:sampling_rate])
		ts_dataset = TimeseriesDataset(tensor_x, tensor_y, seq_len=96,window = int(horizon*12/sampling_rate))
		# train_loader_temp = torch.utils.data.DataLoader(train_dataset, batch_size=32, shuffle=False)
		oTrainLoaders.append(torch.utils.data.DataLoader(ts_dataset, batch_size=batch_size, shuffle=True))
# =============================================================================
# 		if i == 0:
# 			ts_dataset_all = ts_dataset
# 		else:
# 			ts_dataset_all = ConcatDataset([ts_dataset_all, ts_dataset])
# =============================================================================
		# if i%1000 ==0:
		#	 print(i,num_train_samples)
# =============================================================================
# 	train_loader = torch.utils.data.DataLoader(ts_dataset_all, batch_size=batch_size, shuffle=True)
# =============================================================================

	toc = time.perf_counter()
	logging.info(f"Training data generation finished in {toc - tic:0.4f} seconds，processed {len(oTrainLoaders)} samples")

	# validation dataset generation
	tic = time.perf_counter()
	oValidateLoaders = []
	for i in range(num_train_samples,num_train_samples+num_val_samples):
		tensor_x = torch.FloatTensor(data_out1[i][0:2016:sampling_rate])
		tensor_y = torch.FloatTensor(target_out1[i][0:2016:sampling_rate])
		ts_dataset = TimeseriesDataset(tensor_x, tensor_y, seq_len=96,window = int(horizon*12/sampling_rate))
		oValidateLoaders.append(torch.utils.data.DataLoader(ts_dataset, batch_size=batch_size, shuffle=True))
		# train_loader_temp = torch.utils.data.DataLoader(train_dataset, batch_size=32, shuffle=False)
# =============================================================================
# 		if i == num_train_samples:
# 			ts_dataset_all = ts_dataset
# 		else:
# 			ts_dataset_all = ConcatDataset([ts_dataset_all, ts_dataset])
# =============================================================================
		# if i%500 ==0:
		#	 print(i,num_train_samples+num_val_samples)
# 	validate_loader = torch.utils.data.DataLoader(ts_dataset_all, batch_size=batch_size, shuffle=True)

	toc = time.perf_counter()
	logging.info(f"Validation data generation finished in {toc - tic:0.4f} seconds，processed {len(oValidateLoaders)} samples")

	# test dataset generation
	tic = time.perf_counter()
	oTestLoaders = []
	for i in range(num_train_samples+num_val_samples,len(data_out1)):
		tensor_x = torch.FloatTensor(data_out1[i][0:2016:sampling_rate])
		tensor_y = torch.FloatTensor(target_out1[i][0:2016:sampling_rate])
		ts_dataset = TimeseriesDataset(tensor_x, tensor_y, seq_len=96,window = int(horizon*12/sampling_rate))
		oTestLoaders.append(torch.utils.data.DataLoader(ts_dataset, batch_size=batch_size, shuffle=True))
		# train_loader_temp = torch.utils.data.DataLoader(train_dataset, batch_size=32, shuffle=False)
# =============================================================================
# 		if i == num_train_samples+num_val_samples:
# 			ts_dataset_all = ts_dataset
# 		else:
# 			ts_dataset_all = ConcatDataset([ts_dataset_all, ts_dataset])
# =============================================================================
		# if i%500 ==0:
		#	 print(i,len(data_out1))
# 	test_loader = torch.utils.data.DataLoader(ts_dataset_all, batch_size=batch_size, shuffle=True)
	toc = time.perf_counter()
	logging.info(f"Testing data generation finished in {toc - tic:0.4f} seconds, processed {len(oTestLoaders)} samples")

	# define the LSTM model
	class LSTM(nn.Module):
		def __init__(self, input_size, hidden_size, num_layers):
			super(LSTM, self).__init__()
			self.hidden_size = hidden_size
			self.num_layers = num_layers
			self.lstm = nn.LSTM(input_size, hidden_size, num_layers, batch_first=True)
			self.fc = nn.Linear(hidden_size, 1)

		def forward(self, x):
			# initialize hidden state and cell state
			h0 = torch.zeros(self.num_layers, x.size(0), self.hidden_size).to(device)
			c0 = torch.zeros(self.num_layers, x.size(0), self.hidden_size).to(device)

			# forward propagate LSTM
			out, _ = self.lstm(x, (h0, c0))

			# decode the hidden state of the last time step
			out = self.fc(out[:, -1, :])
			return out


	# define hyperparameters
	num_epochs = 10
	learning_rate = 0.001
	hidden_size = 64
	num_layers = 2

	# initialize the model and move it to the GPU
	device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
	model = LSTM(input_size=data_out1.shape[2], hidden_size=hidden_size, num_layers=num_layers).to(device)

	
	logging.info(f"Input layer size: {data_out1.shape[2]} ")
	
	# define the loss function and optimizer
	criterion = nn.MSELoss()
	optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)

	# train the model
	nBatches = len(oTrainLoaders)
	for epoch in range(num_epochs):
		tic_ep = time.perf_counter()
		model.train()
		train_loss = 0.0  # initialize train_loss for each epoch
		dPercent = .1
		for x, oLoader in enumerate(oTrainLoaders):
			if x / nBatches > dPercent:
				logging.info('{:.0f}% complete for epoch {} for hour {} of online training!@#'.format(dPercent * 100, epoch +1, horizon))
				dPercent += .1
			for i, (inputs, targets) in enumerate(oLoader):
				# move inputs and targets to the GPU
				inputs = inputs.to(device)
				targets = targets.to(device)
	
				# forward + backward + optimize
				outputs = model(inputs)
				loss = criterion(outputs, targets)
				optimizer.zero_grad()
				loss.backward()
				optimizer.step()
	
				train_loss += loss.item()  # accumulate loss for each batch

		# validate the model
		model.eval()
		val_loss = 0.0
		with torch.no_grad():
			for x, oLoader in enumerate(oValidateLoaders):
				for inputs, targets in oLoader:
					inputs = inputs.to(device)
					targets = targets.to(device)
					outputs = model(inputs)
					loss = criterion(outputs, targets)
					val_loss += loss.item()
		toc_ep = time.perf_counter()
		epoch_time = toc_ep-tic_ep
		# print statistics
		logging.info(f'Epoch [{epoch+1}/{num_epochs}], Train Loss: {train_loss/len(oTrainLoaders):.6f}, Val Loss: {val_loss/len(oValidateLoaders):.6f}, Time: {epoch_time:.2f} sec')
	toc = time.perf_counter()
	logging.info(f"Training finished in {toc - tic:0.4f} seconds")

	# Evaluate the model on the test set
	tic_test = time.perf_counter()
	model.eval()
	test_loss = 0.0
	with torch.no_grad():
		for x, oLoader in enumerate(oTestLoaders):
			for inputs, targets in oLoader:
				inputs = inputs.to(device)
				targets = targets.to(device)
				outputs = model(inputs)
				loss = criterion(outputs, targets)
				test_loss += loss.item()
	toc_test = time.perf_counter()
	logging.info(f'Test Loss: {test_loss/len(oTestLoaders):.6f}, Time: {(toc_test-tic_test):.2f} sec')

	return model


#save the model
# torch.save(model.state_dict(), 'D:\\Research\\IMRCP\\LSTM\\trained model\\lstm_model_0406.pth')

#%% save the model
# torch.save(model.state_dict(), 'D:\\Research\\IMRCP\\Classification\\final_package\\oneshot_0421.pth')