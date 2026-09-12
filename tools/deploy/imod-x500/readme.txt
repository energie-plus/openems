# How to use the scripts in this folder:

*.225 = EMS7 = test device


$ chmod +x tools/deploy/imod-x500/gpio-init.sh

# Copy script to a stable location on the device
scp tools/deploy/imod-x500/gpio-init.sh root@192.168.1.225:/usr/local/sbin/gpio-init.sh
ssh root@192.168.1.225 'chmod +x /usr/local/sbin/gpio-init.sh'

# Install and enable the systemd unit
scp tools/deploy/imod-x500/gpio-init.service root@192.168.1.225:/etc/systemd/system/gpio-init.service
ssh root@192.168.1.225 'systemctl daemon-reload && systemctl enable --now gpio-init.service'



Klare Diagnose — genau das Windows-Zeilenenden-Problem (\r\n):
--------------------------------------------------------------

/usr/bin/env: 'sh\r': No such file or directory


Der Shebang wird als #!/usr/bin/env sh\r interpretiert. Ein Einzeiler fixt das:

bash:
sed -i 's/\r//' /usr/local/sbin/gpio-init.sh
systemctl restart gpio-init.service
systemctl status gpio-init.service
Dauerhaft verhindern (auf Windows-Seite)
In deinem Git-Repo .gitattributes ergänzen:

Dauerhaft verhindern (auf Windows-Seite)
In deinem Git-Repo .gitattributes ergänzen:

tools/deploy/imod-x500/*.sh  text eol=lf
tools/deploy/imod-x500/*.service  text eol=lf

Oder global in Git Bash:

git config --global core.autocrlf input
Das sorgt dafür dass Shell-Skripte beim Checkout immer LF behalten und du den sed-Fix nicht nach jedem scp erneut brauchst.