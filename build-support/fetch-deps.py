"""Fetch exact official compilation inputs for the optional non-Loom build.
Minecraft 26.3 ships unobfuscated; no remapping or game distribution is needed.
All game libraries stay in .build-deps and are NOT included in the mod JAR.
"""
import concurrent.futures, hashlib, json, urllib.request, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'.build-deps'
DEST.mkdir(exist_ok=True)
def get(url):
    with urllib.request.urlopen(url,timeout=90) as r: return r.read()
def download(url,path,sha1=None):
    if path.exists() and (not sha1 or hashlib.sha1(path.read_bytes()).hexdigest()==sha1): return
    data=get(url)
    if sha1 and hashlib.sha1(data).hexdigest()!=sha1: raise ValueError('Checksum mismatch: '+str(path))
    path.write_bytes(data)
manifest=json.loads(get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
version=next(v for v in manifest['versions'] if v['id']=='26.3')
metadata=get(version['url'])
assert hashlib.sha1(metadata).hexdigest()==version['sha1']
mc=json.loads(metadata)
(DEST/'minecraft-26.3.json').write_bytes(metadata)
work=[]
client=mc['downloads']['client']
work.append((client['url'],DEST/'minecraft-client.jar',client['sha1']))
for lib in mc['libraries']:
    if 'natives' in lib['name'] or 'artifact' not in lib.get('downloads',{}): continue
    a=lib['downloads']['artifact'];work.append((a['url'],DEST/a['path'].replace('/','_'),a['sha1']))
for group,artifact,version in [('net/fabricmc','fabric-loader','0.19.5'),('net/fabricmc/fabric-api','fabric-api','0.163.0+26.3')]:
    url=f'https://maven.fabricmc.net/{group}/{artifact}/{version}/{artifact}-{version}.jar'
    checksum=get(url+'.sha1').decode().strip().split()[0]
    work.append((url,DEST/f'{artifact}.jar',checksum))
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    for _ in pool.map(lambda args: download(*args),work): pass
with zipfile.ZipFile(DEST/'fabric-api.jar') as archive:
    for name in archive.namelist():
        if name.startswith('META-INF/jars/') and name.endswith('.jar'):
            (DEST/Path(name).name).write_bytes(archive.read(name))
print(f'Verified {len(work)} official artifacts in {DEST}')

versions=json.loads(get('https://api.modrinth.com/v2/project/modmenu/version?game_versions=%5B%2226.3%22%5D'))
modmenu=next(v for v in versions if v['version_number']=='21.0.0')
artifact=next(f for f in modmenu['files'] if f['primary'])
data=get(artifact['url'])
if hashlib.sha512(data).hexdigest()!=artifact['hashes']['sha512']: raise ValueError('Mod Menu checksum mismatch')
(DEST/'modmenu-21.0.0.jar').write_bytes(data)
print('Verified optional Mod Menu 21.0.0 compile input')
