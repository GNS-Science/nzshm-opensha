Build the docker image with

```shell
cd jupyterLog
docker build -t nzshm-opensha-jupyterlog .
```

then from the same directory, run the docker image with 

```shell
docker run -it --rm -v ./logs:/home/jovyan/logs -v ./notebooks:/home/jovyan/notebooks --name opensha-jupyterlogs -p 8888:8888 nzshm-opensha-jupyterlog
```

Then follow the link printed on the console, for example 

```shell
http://localhost:8888/lab?token=aadb4261d3234fb7dee647d5ccbaece4a45a199e26e16648
```

The token will change with each run.

Now Jupyter logs will be accessible from `JupyterLab`
