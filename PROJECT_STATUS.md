# IING STD Memory Reader

## Source status
- Read-only USB BOT (READ CAPACITY and READ only); stop on transport failure.
- FAT12/16/32 and exFAT browsing, primary/backup boot discovery, optional manual partition offset.
- Folder search, category filters, photo preview, file export and recursive folder export.
- JPEG and sector-aligned video carving; separate suggested checkpoints, not automatic resume.
- Saved-file gallery using external viewers, cancellation, low battery guard, custom icon.

## Known limitations
- Latest push build and unit tests not verified; commit workflow lookup returned no PR runs.
- No automatic BOT reset/reconnect or bad-sector skipping. Transport failure does not prove media damage.
- Checkpoints are not bound to card identity and may duplicate files or miss incomplete candidates.
- JPEG max 16 MiB; video max 256 MiB; fragmented files may not recover.
- Folder export requires Android 10+, omits empty folders, stops on error, sanitizes names.
- Backup boot discovery is limited; GPT and complete exFAT boot checksum validation absent.
- OS mounting is outside app control. Never format original media.

## Release gates
1. Verify latest push Actions build and unit tests.
2. Test FAT12/16/32 and exFAT fixtures.
3. Test normal USB attach and permission.
4. Test detach and cancellation.
5. Test folder copy and nested paths.
6. Test saved gallery and playback.
7. Test checkpoint resume on expendable card.
8. Verify backup boot discovery.
9. Compare exported files with known hashes.
10. Test APK on actual Android device before release.

Never test first on irreplaceable media.
