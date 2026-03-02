pipeline {
    agent any
    tools {
        maven 'maven_3'
        jdk 'jdk17' 
    }
    environment {
        GOOGLE_APPLICATION_CREDENTIALS = credentials('gcp-terraform-key')
    }
    stages {
        stage('CI: Build App') {
            steps {
                // We create a specific folder for the App
                dir('app-code') {
                    git branch: 'main', url:'https://github.com/vrdeepu/helloworld-gcp.git'
                    // Now Maven runs INSIDE the 'app-code' folder where the pom.xml is
                    bat 'dir /s /b pom.xml'
                    bat 'mvn clean package'
                }
            
                
            }
        }

        stage('CD: Infrastructure Plan') {
            steps {
                // We create a specific folder for the Infra
                dir('infra-code') {
                   git branch: 'main', url:'https://github.com/vrdeepu/helloworld-infra.git'
                    bat 'terraform init'
                    bat 'terraform plan'
                }
            }
        }
    }
}
