FROM quay.io/jupyter/base-notebook:latest

RUN pip install geojson ipyleaflet jupyterlab-geojson matplotlib pyarrow pandas

RUN mkdir -p /home/jovyan/nzshm_utilities
COPY ./nzshm_utils /home/jovyan/nzshm_utilities
WORKDIR /home/jovyan/nzshm_utilities
RUN pip install -e .
WORKDIR /home/jovyan/

USER jovyan
RUN echo "c.NotebookApp.token = ''" > ~/.jupyter/jupyter_notebook_config.py
