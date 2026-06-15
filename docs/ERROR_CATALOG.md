# Error Catalog

| Error | User-facing message | Technical cause | Suggested fix | Retry safe |
| --- | --- | --- | --- | --- |
| Orthanc unavailable | Orthanc 연결 실패 | Orthanc container is down or credentials/URL are wrong | Check `docker compose logs orthanc` and service health | Yes |
| AI service unavailable | AI 작업 실패 | FastAPI service is down or timed out | Check `docker compose logs ai-service` | Yes |
| Invalid DICOM | QC FAIL | DICOM parser cannot read expected tags | Use generated synthetic DICOM or convert to supported format | Yes |
| Unsupported Transfer Syntax | QC FAIL | TransferSyntaxUID is compressed or unsupported | Convert to Explicit VR Little Endian | Yes |
| Missing PixelData | QC FAIL | DICOM object has no PixelData | Use image storage instance with PixelData | Yes |
| QC blocked | AI inference blocked | QC status is FAIL | Review QC suggested fixes | After fixing input |
| STOW failed | AI job failed | Orthanc rejected STOW-RS upload | Check Orthanc logs and generated DICOM validity | Yes |
| Read-back failed | Completed unverified | STOW succeeded but generated instance was not found by UID query | Check generated UIDs and Orthanc logs | Yes |
| Artifact missing | Artifact not found | Overlay/heatmap/result DICOM was not stored or job failed early | Re-run AI job | Yes |
| Session expired | Login required | Demo session cookie expired or backend restarted | Log in again with `demo/demo` | Yes |
