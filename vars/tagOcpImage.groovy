#!/usr/bin/env groovy

def call () {
       
	echo "readFile version "	
        def VERSION = readFile 'app-root/version'
        
	echo "$VERSION"
        openshiftTag alias: 'false', destStream: 'tarun-spog', destTag: "$VERSION", destinationNamespace: '', srcStream: 'tarun-spog', srcTag: 'master', verbose: 'false'
 }


