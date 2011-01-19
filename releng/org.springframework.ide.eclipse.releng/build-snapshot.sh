#!/bin/sh
################################################################################
# Copyright (c) 2005, 2008 Spring IDE Developers
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
# 
# Contributors:
#     Spring IDE Developers - initial API and implementation
################################################################################

WORKSPACE=`pwd`
NAME=`date +%Y%m%d%H%M`
REMOTE_PATH=$1
S3_FILE=$2
QUALIFIER=$3
shift
shift
shift
ARGS=$@

./build.sh -Ds3.publish=$REMOTE_PATH -Dpack200.enable=true -Dp2.enable=true -propertyfile $S3_FILE -DforceContextQualifier=$NAME-$QUALIFIER $ARGS

if [ $? -ne 0 ]
then
    exit 1
fi