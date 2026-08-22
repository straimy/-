from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"

state = JAVA / "GgoSupplyMapState.java"
adapter = JAVA / "GgoRuntimeV1ContractMapAdapter.java"
completion = JAVA / "GgoContractCompletionState.java"
for path in (state, adapter, completion):
    if not path.exists():
        raise SystemExit(f"Stage 35: missing {path}; apply Stage 27 first")

s = state.read_text(encoding="utf-8")
anchor = "    public static Snapshot snapshot(){return snapshot;}\n"
clear_method = '''    public static void clear(){
        snapshot=new Snapshot("",false,0,0,0,0,0,List.of());
    }
'''
if anchor in s and "public static void clear()" not in s:
    s = s.replace(anchor, clear_method + anchor, 1)
state.write_text(s, encoding="utf-8")

a = adapter.read_text(encoding="utf-8")
if "import net.minecraft.client.Minecraft;" not in a:
    a = a.replace("package arena.client.shell;\n\n", "package arena.client.shell;\n\nimport net.minecraft.client.Minecraft;\n", 1)
field_anchor = "    private static long lastRequest;\n"
if field_anchor in a and "sessionConnection" not in a:
    a = a.replace(field_anchor, field_anchor + "    private static Object sessionConnection;\n", 1)
old_tick = '''    public static void tick(){
        install();if(!installed||request==null)return;
        long now=System.currentTimeMillis();if(now-lastRequest<1200L)return;lastRequest=now;
'''
new_tick = '''    public static void tick(){
        install();
        Object currentConnection=Minecraft.getInstance().getConnection();
        if(currentConnection!=sessionConnection){
            sessionConnection=currentConnection;
            lastRequest=0L;
            GgoSupplyMapState.clear();
        }
        if(!installed||request==null||currentConnection==null)return;
        long now=System.currentTimeMillis();if(now-lastRequest<1200L)return;lastRequest=now;
'''
if old_tick in a:
    a = a.replace(old_tick, new_tick, 1)
elif "GgoSupplyMapState.clear();" not in a:
    raise SystemExit("Stage 35: map adapter tick anchor missing")
adapter.write_text(a, encoding="utf-8")

c = completion.read_text(encoding="utf-8")
if "import net.minecraft.client.Minecraft;" not in c:
    c = c.replace("package arena.client.shell;\n\n", "package arena.client.shell;\n\nimport net.minecraft.client.Minecraft;\n", 1)
field_anchor = "    private static Popup popup;\n"
if field_anchor in c and "sessionConnection" not in c:
    c = c.replace(field_anchor, field_anchor + "    private static Object sessionConnection;\n", 1)
old_poll = '''    public static void poll(){
        var entries=GgoContractState.entries();
        if(entries.isEmpty())return;
'''
new_poll = '''    public static void poll(){
        Object currentConnection=Minecraft.getInstance().getConnection();
        if(currentConnection!=sessionConnection){
            sessionConnection=currentConnection;
            previous.clear();
            initialized=false;
            popup=null;
        }
        var entries=GgoContractState.entries();
        if(entries.isEmpty()||currentConnection==null)return;
'''
if old_poll in c:
    c = c.replace(old_poll, new_poll, 1)
elif "previous.clear();" not in c:
    raise SystemExit("Stage 35: completion session anchor missing")
completion.write_text(c, encoding="utf-8")

checks = {
    state: "public static void clear()",
    adapter: "currentConnection!=sessionConnection",
    adapter: "GgoSupplyMapState.clear();",
    completion: "previous.clear();",
}
for path, marker in checks.items():
    if marker not in path.read_text(encoding="utf-8"):
        raise SystemExit(f"Stage 35: marker missing in {path}: {marker}")

print("GGO Contract Session Stage 35 applied")
print(" - reconnect clears stale supply/extraction/balance snapshot")
print(" - reconnect primes completed contracts without replaying old popup")
print(" - first request in a new connection is sent immediately")
