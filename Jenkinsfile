// builds and tests the four services one at a time, never in parallel: their tests share the finconsult_test
// database. Then packages all four
pipeline {
    agent any

    environment {
        SPRING_DATASOURCE_URL = 'jdbc:postgresql://postgres:5432/finconsult_test'
        // the tests read the database from TEST_DB_URL (with DB_USERNAME and DB_PASSWORD from the container)
        TEST_DB_URL = 'jdbc:postgresql://postgres:5432/finconsult_test'
        REGISTRY = '295663608868.dkr.ecr.us-east-1.amazonaws.com'
        AWS_DEFAULT_REGION = 'us-east-1'
        VERSION = "1.0.${BUILD_NUMBER}"
    }

    stages {
        stage('notification-service: build') {
            steps { dir('notification-service') { sh 'mvn clean compile -q' } }
        }
        stage('notification-service: test') {
            steps { dir('notification-service') { sh 'mvn test' } }
        }

        stage('user-service: build') {
            steps { dir('user-service') { sh 'mvn clean compile -q' } }
        }
        stage('user-service: test') {
            steps { dir('user-service') { sh 'mvn test' } }
        }

        stage('booking-service: build') {
            steps { dir('booking-service') { sh 'mvn clean compile -q' } }
        }
        stage('booking-service: test') {
            steps { dir('booking-service') { sh 'mvn test' } }
        }

        stage('document-service: build') {
            steps { dir('document-service') { sh 'mvn clean compile -q' } }
        }
        stage('document-service: test') {
            steps { dir('document-service') { sh 'mvn test' } }
        }

        stage('package') {
            steps {
                dir('notification-service') { sh 'mvn package -DskipTests -q' }
                dir('user-service') { sh 'mvn package -DskipTests -q' }
                dir('booking-service') { sh 'mvn package -DskipTests -q' }
                dir('document-service') { sh 'mvn package -DskipTests -q' }
            }
        }

        // same build contexts as docker-compose.yml, tagged with the version, the short commit and latest
        stage('Docker Build') {
            steps {
                script {
                    env.GIT_SHA = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                }
                withCredentials([string(credentialsId: 'ec2-host', variable: 'EC2_HOST')]) {
                    sh '''#!/bin/bash
                        set -euo pipefail
                        for s in user-service booking-service notification-service document-service; do
                            docker build -t "$REGISTRY/finconsult-$s:$VERSION" -t "$REGISTRY/finconsult-$s:$GIT_SHA" -t "$REGISTRY/finconsult-$s:latest" "$s"
                        done
                        docker build --build-arg VITE_API_URL="http://$EC2_HOST:8080" \
                            -t "$REGISTRY/finconsult-frontend:$VERSION" -t "$REGISTRY/finconsult-frontend:$GIT_SHA" -t "$REGISTRY/finconsult-frontend:latest" frontend
                    '''
                }
            }
        }

        stage('Push to ECR') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'aws-ecr', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    sh '''#!/bin/bash
                        set -euo pipefail
                        aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin "$REGISTRY"
                        for s in user-service booking-service notification-service document-service frontend; do
                            for t in "$VERSION" "$GIT_SHA" latest; do
                                docker push "$REGISTRY/finconsult-$s:$t"
                            done
                        done
                    '''
                }
            }
        }

        // the instance logs in to ecr with its own iam role. Only the app services are started, never jenkins
        stage('Deploy') {
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'ec2-ssh', keyFileVariable: 'SSH_KEY'),
                                 string(credentialsId: 'ec2-host', variable: 'EC2_HOST')]) {
                    sh '''#!/bin/bash
                        set -euo pipefail
                        ssh -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new "ec2-user@$EC2_HOST" "REGISTRY=$REGISTRY TAG=$VERSION bash -s" <<'EOF'
set -euo pipefail
cd ~/FinConsult
git pull
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin "$REGISTRY"
export REGISTRY TAG
SERVICES="frontend gateway notification-service user-service booking-service document-service"
docker-compose -f docker-compose.yml -f docker-compose.prod.yml pull $SERVICES
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-build $SERVICES
docker-compose -f docker-compose.yml -f docker-compose.prod.yml restart gateway
EOF
                    '''
                }
            }
        }
    }

    post {
        success { echo 'All four services built, tested and packaged.' }
        failure { echo 'The pipeline failed, see the stage that is red.' }
    }
}
