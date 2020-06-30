FROM openjdk:12-jdk-alpine
LABEL Chris Chamberlain <chrisbc@artisan.co.nz>
RUN apk add --no-cache curl tar bash procps \
	python3 git \
	fontconfig ttf-dejavu

WORKDIR /opensha

RUN git clone https://github.com/opensha/opensha-commons.git &&\
	git clone https://github.com/opensha/opensha-core.git &&\
	git clone https://github.com/opensha/opensha-ucerf3.git &&\
	git clone https://github.com/opensha/opensha-apps.git &&\ 
	git clone https://github.com/opensha/opensha-dev.git

#  py4j demo setup
RUN pip3 install py4j
RUN	git clone https://github.com/GNS-Science/opensha-py.git

WORKDIR /opensha/opensha-core
RUN ./gradlew fatJar

WORKDIR /opensha/opensha-ucerf3
RUN ./gradlew fatJar

ENV OPENSHA_PY_CLASSPATH /opensha/opensha-core/build/libs/opensha-core-all.jar:/opensha/opensha-ucerf3/build/libs/opensha-ucerf3-all.jar:/usr/share/py4j/py4j0.10.9.jar:/opensha/opensha-py/src/java

WORKDIR /opensha
RUN javac -cp $OPENSHA_PY_CLASSPATH opensha-py/src/java/org/opensha/py/HazardCurveCalcGateway.java

CMD [""]