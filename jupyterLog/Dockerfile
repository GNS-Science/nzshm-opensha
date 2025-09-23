FROM quay.io/jupyter/base-notebook:latest

RUN pip install geojson ipyleaflet jupyterlab-geojson matplotlib pyarrow pandas

USER jovyan
RUN echo "c.NotebookApp.token = ''" > ~/.jupyter/jupyter_notebook_config.py
