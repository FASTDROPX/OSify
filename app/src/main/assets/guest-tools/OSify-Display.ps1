param([switch]$Install)
$ErrorActionPreference='Stop'
$base='__OSIFY_URL__'
if($Install) {
  $dir=Join-Path $env:LOCALAPPDATA 'OSify'
  New-Item -ItemType Directory -Force $dir | Out-Null
  $dest=Join-Path $dir 'Display.ps1'
  Copy-Item -LiteralPath $PSCommandPath -Destination $dest -Force
  $startup=[Environment]::GetFolderPath('Startup')
  $command='@powershell.exe -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "'+$dest+'"'
  [IO.File]::WriteAllText((Join-Path $startup 'OSify Display.cmd'),$command,[Text.Encoding]::ASCII)
}
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class OSifyDisplay {
 [StructLayout(LayoutKind.Explicit, CharSet=CharSet.Unicode, Size=220)]
 public struct Mode {
  [FieldOffset(68)] public ushort size;
  [FieldOffset(72)] public uint fields;
  [FieldOffset(172)] public uint width;
  [FieldOffset(176)] public uint height;
 }
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern bool EnumDisplaySettings(string device,int mode,ref Mode data);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern int ChangeDisplaySettings(ref Mode data,uint flags);
 public static int[] Current() {Mode m=new Mode();m.size=220;EnumDisplaySettings(null,-1,ref m);return new int[]{(int)m.width,(int)m.height};}
 public static int Resize(int w,int h) {
  Mode m=new Mode();m.size=220;if(!EnumDisplaySettings(null,-1,ref m))return -1;
  m.width=(uint)w;m.height=(uint)h;m.fields|=0x180000;
  int result=ChangeDisplaySettings(ref m,2);return result==0?ChangeDisplaySettings(ref m,0):result;
 }
}
"@
$last=''
$result='Ready'
while($true) {
 try {
  $desired=Invoke-RestMethod -Uri ($base+'display') -TimeoutSec 4
  if($desired.enabled) {
   $w=[int]$desired.width;$h=[int]$desired.height;$key="$w x $h"
   if($w -ge 480 -and $h -ge 320 -and $w -le 1920 -and $h -le 1920 -and $key -ne $last) {
    $code=[OSifyDisplay]::Resize($w,$h)
    $result=if($code -eq 0){'Applied'}else{"Display driver rejected mode ($code)"}
    $last=$key
   }
  }
  else {$last='';$result='Automatic resolution disabled'}
  $mode=[OSifyDisplay]::Current()
  $body=@{width=$mode[0];height=$mode[1];message=$result;agent='Windows PowerShell'} | ConvertTo-Json -Compress
  Invoke-RestMethod -Method Post -Uri ($base+'status') -ContentType 'application/json' -Body $body -TimeoutSec 4 | Out-Null
 } catch {}
 Start-Sleep -Seconds 2
}
