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
                dir('app-code') {
                    git branch: 'main', url:'https://github.com/vrdeepu/helloworld-gcp.git'
                    bat 'mvn clean package'
                }
            }
        }

        stage('CD: Infra Apply') {
            steps {
                dir('infra-code') {
                    git branch: 'main', url:'https://github.com/vrdeepu/helloworld-infra.git'
                    echo "Initializing and Provisioning..."
                    bat 'terraform init'
                    bat 'terraform apply -auto-approve'
                }
            }
        }

        stage('CD: Deploy JAR to VM') {
            steps {
                dir('app-code') {
                    bat """
                        @echo off
                        :: 1. Crucial: Wait 5 minutes for the VM's Startup Script to finish installing Java
                        echo Waiting 5 minutes for VM initialization...
                        ping 127.0.0.1 -n 301 > nul
                        :: 3. Explicitly set the project to avoid "Resource Not Found" errors
                        call gcloud config set project project-cb063053-ca79-4fea-9b1

                        :: 3. Copy the JAR using the correct VM name: basic-hello-vm
                        echo Uploading JAR to VM...
                        call gcloud compute scp target\\helloworld-gcp-1.0-SNAPSHOT.jar basic-hello-vm:/tmp/app.jar --zone=us-central1-a --quiet

                        :: 4. Use 'nohup' and redirect logs so the app doesn't die when the SSH session ends
                        echo Starting Application in background...
                        call gcloud compute ssh basic-hello-vm --zone=us-central1-a --quiet --command="nohup java -jar /tmp/app.jar > /tmp/app.log 2>&1 &"
        
                        echo Deployment command sent successfully.
                    """
                }

            }
        }

        stage('Cleanup: Manual Approval') {
            steps {
                script {
                    input message: "App is deployed! Check the IP, then click proceed to DESTROY.", ok: "Destroy Now"
                }
                dir('infra-code') {
                    echo "Cleaning up Infrastructure..."
                    bat 'terraform destroy -auto-approve'
                }
            }
        }
    }
}