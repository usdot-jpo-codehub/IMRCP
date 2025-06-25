/* 
 * Copyright 2017 Federal Highway Administration.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */	
const minutesToHHmm = (minutes, excludeSign) =>
{

  var sign;
  if (minutes < 0)
  {
    minutes *= -1;
    sign = '-';
  }
  else if (minutes > 0)
    sign = '+';
  else
    sign = ' ';

  var remainderMinutes = minutes % 60;
  var hours = (minutes - remainderMinutes) / 60;
  minutes = remainderMinutes;
  if (minutes === 0)
    minutes = '00';

  if (excludeSign)
    return  hours + ':' + minutes;
  else
    return sign + hours + ':' + minutes;
};

const minutesToHH = (minutes, excludeSign) =>
{
    var HHmm = minutesToHHmm(minutes, excludeSign);
    return HHmm.substr(0, HHmm.length - 3);
};


function createMenu()
{
	
}

export {minutesToHH, minutesToHHmm};