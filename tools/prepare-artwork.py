from pathlib import Path
import json,os,hashlib,xml.etree.ElementTree as ET
os.environ['QT_QPA_PLATFORM']='offscreen'
from PySide6.QtGui import QImage,QPainter,QColor,QGuiApplication,QFont
from PySide6.QtSvg import QSvgRenderer
from PySide6.QtCore import Qt,QRectF
import numpy as np
app=QGuiApplication([])
root=Path(__file__).resolve().parent.parent
originals=root/'third_party/artwork-originals'
dest=root/'app/src/main/assets/branding';dest.mkdir(parents=True,exist_ok=True)
records=json.loads((originals/'sources.json').read_text('utf-8'))
def load(p):
 if p.suffix=='.svg':
  data=p.read_text('utf-8');tree=ET.fromstring(data)
  # Quickemu adds optional white display discs; keep the real mark, remove that container.
  for parent in tree.iter():
   for child in list(parent):
    if child.tag.endswith('circle') and child.attrib.get('fill','').lower() in ('#fff','#ffffff','white') and float(child.attrib.get('r','0'))>=200:parent.remove(child)
  svg=QSvgRenderer(ET.tostring(tree));size=svg.defaultSize();size.scale(2048,2048,Qt.AspectRatioMode.KeepAspectRatio)
  im=QImage(size,QImage.Format.Format_ARGB32);im.fill(Qt.GlobalColor.transparent)
  painter=QPainter(im);svg.render(painter);painter.end();return im
 return QImage(str(p)).convertToFormat(QImage.Format.Format_ARGB32)
def array(im):return np.frombuffer(im.bits(),dtype=np.uint8).reshape(im.height(),im.bytesPerLine()//4,4)[:,:im.width()]
def trim(im):
 a=array(im);ys,xs=np.where(a[:,:,3]>30)
 if not len(xs):raise ValueError('empty image')
 return im.copy(int(xs.min()),int(ys.min()),int(xs.max()-xs.min()+1),int(ys.max()-ys.min()+1))
seen=set();okay=[]
for row in records:
 if 'error' in row or (row['key'],row['kind']) in seen:continue
 p=originals/row['original'];im=load(p)
 if im.isNull():print('INVALID',row['key'],row['kind']);continue
 key=row['key'].replace('-','_');kind=row['kind']
 if kind=='logo':
  if key in ('windowsxp','windows7'):
   # Preserve the actual original flag; omit the adjacent wordmark used in the source SVG.
   if key=='windows7':im=im.copy(0,0,int(im.width()*.235),im.height())
   else:im=im.copy(0,0,int(im.height()*1.15),im.height())
  im=trim(im)
  # Remove a plain corner-connected white canvas only, keeping enclosed white logo details.
  a=array(im)
  if np.all(a[0,0,:3]>245) and a[0,0,3]>245 and np.all(a[-1,-1,:3]>245):
   white=np.all(a[:,:,:3]>245,axis=2);pending=[(0,0),(im.width()-1,0),(0,im.height()-1),(im.width()-1,im.height()-1)];visited=set()
   while pending:
    x,y=pending.pop()
    if (x,y) in visited or x<0 or y<0 or x>=im.width() or y>=im.height() or not white[y,x]:continue
    visited.add((x,y));a[y,x,3]=0;pending.extend(((x+1,y),(x-1,y),(x,y+1),(x,y-1)))
   im=trim(im)
  result=QImage(320,320,QImage.Format.Format_ARGB32);result.fill(Qt.GlobalColor.transparent)
  fit=im.scaled(300,267,Qt.AspectRatioMode.KeepAspectRatio,Qt.TransformationMode.SmoothTransformation)
  paint=QPainter(result);paint.drawImage((320-fit.width())//2,(320-fit.height())//2,fit);paint.end()
  result.save(str(dest/f'{key}-logo.png'));im=result
 else:
  im=im.scaled(1024,768,Qt.AspectRatioMode.KeepAspectRatio,Qt.TransformationMode.SmoothTransformation)
  im.save(str(dest/f'{key}-cover.webp'),'WEBP',84)
 seen.add((row['key'],kind));okay.append({**row,'asset':f'branding/{key}-{kind}.{"png" if kind=="logo" else "webp"}','sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
keys=sorted({r['key'] for r in okay})
sheet=QImage(1080,((len(keys)+3)//4)*190,QImage.Format.Format_RGB32);sheet.fill(QColor('#10151b'))
from PySide6.QtGui import QFontDatabase
fontid=QFontDatabase.addApplicationFont(str(root/'app/src/main/res/font/comfortaa.ttf'))
family=QFontDatabase.applicationFontFamilies(fontid)[0]
paint=QPainter(sheet);paint.setFont(QFont(family,12));paint.setPen(QColor('white'))
for i,key in enumerate(keys):
 x=(i%4)*270;y=(i//4)*190;k=key.replace('-','_')
 cover=QImage(str(dest/f'{k}-cover.webp'));logo=QImage(str(dest/f'{k}-logo.png'))
 if not cover.isNull():paint.drawImage(QRectF(x+6,y+6,258,135),cover)
 if not logo.isNull():paint.drawImage(QRectF(x+12,y+92,52,52),logo)
 paint.drawText(x+12,y+170,key)
paint.end();sheet.save(str(root/'docs/artwork-contact-sheet.png'))
(dest/'sources.json').write_text(json.dumps(okay,ensure_ascii=False,indent=2),'utf-8')
print('Prepared',len(okay),'assets for',len(keys),'systems')
