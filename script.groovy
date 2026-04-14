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

        stage('CD: Upload JAR to GCS') {
            steps {
                dir('app-code') {
                    script {
                        // 1. Set the project
                        bat "call gcloud config set project project-cb063053-ca79-4fea-9b1"

                        // 2. Upload the JAR to GCS
                        // Note: Ensure the bucket name matches exactly what Terraform created
                        echo "Uploading JAR to GCS Bucket..."
                        bat "call gsutil cp target\\helloworld-gcp-1.0-SNAPSHOT.jar gs://helloworld-binaries-project-cb063053-ca79-4fea-9b1/app.jar"

                        echo "Upload Complete. Binary is now staged in GCS."
                    }
                }
            }
        } // End of Stage

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