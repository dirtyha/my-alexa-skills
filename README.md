# my-alexa-skills
This repository contains few of my Alexa custom skills. I use these skills to monitor and control my home automation system with voice. All the listed skills connect to my home automation system by using RESTful API calls. These skills have currently not been published in Amazon developer portal since the REST API they use is not following any standards used in commercial home automation systems.

## lights-control
This skill provides capability to switch on/off lights at my home. Lights are driven by IHC which is connected to home automation via RS-232 link. For example: Alexa, ask lighs control to switch on kitchen cabinets. 

## heating-control
This skill is capable of monitoring and controlling Mitsubishi FD35 air-to-air heat pump. The pump is diven by a custom made IR remote which is connected to home automation. For example: Alexa, ask heating control to set temperature 23.

## home-measurements
This skill is capable of monitoring some temperature and electric power consumption parameters. Temperature measurements are based on 1-wire sensors and power consumption on Cost Control receiver/display and a buch of transmitter modules. For example: Alexa, ask my home power consumption?

## time-table
This skill can query [Reittiopas API](http://developer.reittiopas.fi/pages/fi/http-get-interface-version-2.php?lang=EN) public transport time tables and speaks departure times for your preferred bus and train stops. For example: Alexa, ask time tables next train.
The AWS Lambda function expects the following environment variables:
- user: reittiopas API user name
- passwd: reittiopas API password
- train_stop: your local train stop code e.g. E5142
- bus_stop: your local bus stop code e.g. E5238
- lines: your preferred bus/train lines in comma separated list e.g. 2065  1,2065K 1,3002E 2,3002L 2,3002L62,3002U 2,3002U32
- application_id: your alexa skill's application id

## TODO
- Integrate my Vaisala GMW90 wall transmitter to home automation system and make Alexe speak measured parameters.
- Listen news podcasts from YLE 
