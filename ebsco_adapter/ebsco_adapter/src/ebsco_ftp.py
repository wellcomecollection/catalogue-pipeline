from ftplib import FTP
import os


class EbscoFtp:
    def __init__(self, ftp_server, ftp_username, ftp_password, ftp_remote_dir):
        self.ftp_server = ftp_server
        self.ftp_username = ftp_username
        self.ftp_password = ftp_password
        self.ftp_remote_dir = ftp_remote_dir

    def __enter__(self):
        self.ftp = FTP(self.ftp_server)
        self.ftp.login(self.ftp_username, self.ftp_password)
        self.ftp.cwd(self.ftp_remote_dir)
        return self

    def list_files(self, valid_suffixes):
        ftp_files = []
        self.ftp.retrlines('LIST', ftp_files.append)
        ftp_files = [file.split()[-1] for file in ftp_files if file.endswith(tuple(valid_suffixes))]
        return ftp_files

    def download_file(self, file, temp_dir):
        with open(os.path.join(temp_dir, file), 'wb') as f:
            print(f"Downloading {file}...")
            self.ftp.retrbinary(f"RETR {file}", f.write)

        return os.path.join(temp_dir, file)

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.ftp.quit()
