#!/usr/bin/env python3
"""OSify optional X11 display helper. No shell commands or arbitrary server instructions."""
import json, os, re, shutil, subprocess, sys, time, urllib.request
from pathlib import Path
BASE='__OSIFY_URL__'
if '--install' in sys.argv:
    target=Path.home()/'.local/share/osify/display.py'
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(Path(__file__).resolve(),target)
    startup=Path.home()/'.config/autostart/osify-display.desktop'
    startup.parent.mkdir(parents=True,exist_ok=True)
    # Desktop Exec quoting, including home paths with spaces.
    executable=str(target).replace('\\','\\\\').replace('"','\\"').replace('`','\\`').replace('$','\\$')
    startup.write_text('[Desktop Entry]\nType=Application\nName=OSify Display\nExec=python3 "'+executable+'"\nTerminal=false\n')
last=None
last_status='Ready'
while True:
    try:
        with urllib.request.urlopen(BASE+'display',timeout=4) as response: desired=json.load(response)
        status='X11/xrandr required';width=height=0
        if shutil.which('xrandr') and os.environ.get('XDG_SESSION_TYPE')!='wayland':
            info=subprocess.run(['xrandr','--query'],capture_output=True,text=True,timeout=5).stdout
            current=re.search(r'current (\d+) x (\d+)',info)
            if current:width,height=map(int,current.groups())
            output=re.search(r'^(\S+) connected',info,re.M)
            w=int(desired.get('width',0));h=int(desired.get('height',0))
            status=last_status
            if desired.get('enabled') and output and 480<=w<=1920 and 320<=h<=1920 and (w,h)!=last:
                # Apply only a mode the guest driver advertises. Never invent an unsafe modeline.
                mode=f'{w}x{h}'
                if re.search(r'^\s+'+re.escape(mode)+r'\s',info,re.M):
                    result=subprocess.run(['xrandr','--output',output.group(1),'--mode',mode],capture_output=True,text=True,timeout=5)
                    status='Applied' if result.returncode==0 else 'Driver rejected mode'
                else:status='Requested mode is not advertised by the display driver'
                last=(w,h)
                last_status=status
            if not desired.get('enabled'):
                last=None
                status=last_status='Automatic resolution disabled'
        body=json.dumps(dict(width=width,height=height,message=status,agent='Linux X11')).encode()
        urllib.request.urlopen(urllib.request.Request(BASE+'status',data=body,headers={'Content-Type':'application/json'}),timeout=4).close()
    except Exception:
        pass
    time.sleep(2)
