FROM python:3.13-slim

COPY requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir --disable-pip-version-check -r /tmp/requirements.txt

WORKDIR /home/jovyan/work

CMD ["jupyter", "notebook", "--ip=0.0.0.0", "--no-browser", "--allow-root", "--ServerApp.token=", "--ServerApp.password=", "--ServerApp.allow_origin=*", "--ServerApp.disable_check_xsrf=True"]
