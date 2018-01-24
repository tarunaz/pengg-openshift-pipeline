#!/usr/bin/env groovy

def call (Map config) {
       
         // Checkout source code for version file
    	checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'app-root']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'a0abb0d8-4a01-4d4e-a4c7-90526325f245', url: gitRepoUrl]]])

	echo "readFile version "	
        def VERSION = readFile 'app-root/version'
        
	echo "$VERSION"
        openshiftTag alias: 'false', destStream: config.microservice, destTag: "$VERSION", destinationNamespace: '', srcStream: config.microservice, srcTag: 'master', verbose: 'false'
 }


