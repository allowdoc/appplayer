import json

data = json.load(open(r"C:\Users\Noval\.gemini\antigravity\scratch\android-runner\data\com.myairtelapp\screen_onboarding.json"))
def walk(node):
    t = node.get("text", "")
    if t:
        print(repr(t), [hex(ord(c)) for c in t])
    for c in node.get("children", []):
        walk(c)
walk(data["root"])
