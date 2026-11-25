FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY sensonet_sim ./sensonet_sim
COPY sim.py .
RUN useradd --system --uid 10001 sim
USER sim
ENV PYTHONUNBUFFERED=1
ENTRYPOINT ["python", "sim.py"]
