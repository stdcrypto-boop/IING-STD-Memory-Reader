# IING STD Memory Reader

Status: planning; no APK built or hardware tested yet.

## V1 roadmap
- Android USB host enumeration and explicit permission.
- USB mass storage BOT, SCSI READ CAPACITY and READ(10), strict response validation; no write, format, repair or erase commands.
- Read-only MBR and FAT16/FAT32 parsing with bounds and cycle checks.
- Export recovered files only to a user-selected phone destination, never source USB.
- Stop on disconnect or repeated I/O errors; no automatic retries on failing media.
- Test with synthetic disk images and expendable cards before valuable media.
- Build APK in GitHub Actions.

Physical failure, unsupported reader protocols and Android USB restrictions may prevent recovery. Android system mounting is outside app control; hardware write blocker recommended for irreplaceable data.
