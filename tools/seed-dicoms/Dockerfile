FROM python:3.12-slim

WORKDIR /seed
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY seed.py .
COPY samples ./samples

CMD ["python", "seed.py", "seed", "--output", "/seed/output", "--replace-existing"]
