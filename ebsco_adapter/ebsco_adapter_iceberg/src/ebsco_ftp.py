import os
from ftplib import FTP


class EbscoFtp:
    def __init__(self, ftp_server: str, ftp_username: str, ftp_password: str, ftp_remote_dir: str) -> None:
        self.ftp_server = ftp_server
        self.ftp_username = ftp_username
        self.ftp_password = ftp_password
        self.ftp_remote_dir = ftp_remote_dir
        self.ftp_connection_open = False

    def __enter__(self) -> "EbscoFtp":
        self.ftp = FTP(self.ftp_server)
        self.ftp.login(self.ftp_username, self.ftp_password)
        self.ftp.cwd(self.ftp_remote_dir)
        self.ftp_connection_open = True
        return self

    def list_files(self, validation) -> list[str]:
        ftp_files: list[str] = []
        self.ftp.retrlines("LIST", ftp_files.append)
        ftp_files = [
            file.split()[-1]
            for file in ftp_files
            if validation(file)
        ]
        return ftp_files

    def download_file(self, file: str, temp_dir: str) -> str:
        with open(os.path.join(temp_dir, file), "wb") as f:
            print(f"Downloading {file}...")
            self.ftp.retrbinary(f"RETR {file}", f.write)

        return os.path.join(temp_dir, file)

    def quit(self) -> None:
        self.ftp.quit()
        self.ftp_connection_open = False

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        if self.ftp_connection_open:
            self.ftp.quit()
