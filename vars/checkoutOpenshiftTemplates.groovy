#!/usr/bin/env groovy

def call (String gitRepoUrl) {

    // Checkout openshift template files
    checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'pengg-openshift']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'a0abb0d8-4a01-4d4e-a4c7-90526325f245', url: ${gitRepoUrl}]]])

}
