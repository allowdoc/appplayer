import urllib.request
import json

url = "https://search.maven.org/solrsearch/select?q=hermesc&rows=20&wt=json"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    data = json.loads(urllib.request.urlopen(req).read().decode('utf-8'))
    for doc in data.get('response', {}).get('docs', []):
        print(doc['g'], doc['a'], doc['latestVersion'])
except Exception as e:
    print(f"Error: {e}")
