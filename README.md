Open Source Overview
============================
The Integrated Modeling for Road Condition Prediction (IMRCP) System integrates weather and traffic data sources and predictive methods to effectively predict road and travel conditions in support of tactical and strategic decisions by travelers, transportation operators, and maintenance providers. The system collects and integrates environmental observations and transportation operations data; collects forecast environmental and operations data when available; initiates road weather and traffic forecasts based on collected data; generates travel and operational alerts from the collected real-time and forecast data; and provides the road condition data, forecasts and alerts to users and other systems.

License information
-------------------
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. Some portions of IMRCP have been developed using software generated using large language models.

System Deployment
-----------------
There are four main areas for success in deploying the IMRCP system: infrastructure, installation, real-time data access, historic data access.

Infrastructure involves the ability to procure cloud-based or physical hardware, acquire network address, configure firewall policies, install operating system, deploy a proxy web server, acquire network security certificates, and access network storage as needed. Completion of these tasks can vary widely by organization, policy, and procedure. The minimum recommended IMRCP hardware requirements are one x64 server with 32 64-bit cores, 256 GB of memory, and 10 TB active storage, and 10 TB backup storage.

Installation of the IMRCP system is straightforward. Download the latest version, unpack, and follow the step-by-step instructions. This task takes less than a day, usually a couple of hours.

The IMRCP system relies heavily upon the availability of real-time traffic data. System deployers will need access to a traffic data feed as well as a traffic event data feed. It is also necessary to build software adapters to tranform the traffic spped and event data to the IMRCP input format specifications.

The IMRCP system also uses machine learning models for its traffic prediction functions. Historic weather conditions, and traffic speed data for at least two years are needed to train a machine learning model specific to a deployer's road network. In many cases, building separate software adapters for the historic data is necessary as historic data frequently originate from sources different from the real-time data.
