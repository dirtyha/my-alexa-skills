# my-alexa-skills
This repository contains few of my Alexa custom skills. I use these skills to monitor and control my home automation system with voice. All the listed skills connect to my home automation system by using RESTful API calls. These skills have currently not been published in Amazon developer portal since the REST API they use is not following any standards used in commercial home automation systems.

## lights-control
This skill provides capability to switch on/off lights at my home. Lights are driven by IHC which is connected to home automation via RS-232 link.

## heating-control
This skill is capable of monitoring and controlling Mitsubishi FD35 air-to-air heat pump. The pump is diven by a custom made IR remote which is connected to home automation.

## home-measurements
This skill is capable of monitoring some temperature and electric power consumption parameters. Temperature measurements are based on 1-wire sensors and power consumption on Cost Control receiver/display and a buch of transmitter modules.

## TODO
Integrate my Vaisala GMW90 wall transmitter to home automation system and make Alexe speak measured parameters.
