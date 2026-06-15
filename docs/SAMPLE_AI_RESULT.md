# Sample AI Result

```json
{
  "modelProvider": "DEMO_FALLBACK",
  "findingLabel": "Opacity demo",
  "score": 0.8421,
  "riskBand": "HIGH_DEMO",
  "boxes": [
    {
      "x": 280,
      "y": 190,
      "width": 120,
      "height": 110,
      "label": "Opacity demo",
      "score": 0.8421
    }
  ],
  "heatmapPngBase64": "<base64-png-placeholder>",
  "overlayPngBase64": "<base64-png-placeholder>",
  "resultDicomBase64": "<base64-dicom-placeholder>",
  "preprocessing": {
    "rows": 512,
    "columns": 512,
    "windowCenter": 40.0,
    "windowWidth": 400.0,
    "rescaleSlope": 1.0,
    "rescaleIntercept": 0.0
  },
  "qcSummary": {
    "status": "PASS",
    "checkCount": 24
  },
  "originalUids": {
    "studyInstanceUid": "1.2.826.0.1.3680043.10.543.20260615.2",
    "seriesInstanceUid": "1.2.826.0.1.3680043.10.543.20260615.2.1",
    "sopInstanceUid": "1.2.826.0.1.3680043.10.543.20260615.2.1.1"
  },
  "generatedResultUids": {
    "studyInstanceUid": "1.2.826.0.1.3680043.10.543.20260615.2",
    "seriesInstanceUid": "2.25.123456789",
    "sopInstanceUid": "2.25.987654321"
  },
  "stowStatus": "STOW_RS_STORED",
  "readBackStatus": "READBACK_VERIFIED",
  "warnings": [
    "No ONNX model was found; deterministic demo fallback was used."
  ],
  "disclaimer": "Demo only. Not for clinical use. No real patient data."
}
```
