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
                // We run this from the root workspace so it can see both 'app-code' and 'infra-code'
                bat """
                    gcloud compute scp app-code/target/helloworld-gcp-1.0-SNAPSHOT.jar helloworld-vm:/tmp/app.jar --zone=us-central1-a --quiet
                    gcloud compute ssh helloworld-vm --zone=us-central1-a --quiet --command="java -jar /tmp/app.jar &"
                """
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
