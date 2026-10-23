"""Wandelt Mojangs ProGuard-Zuordnung (client_mappings.txt) in das SRG-Format für SpecialSource um (obf -> Name).

Aufruf: python3 tools/proguard2srg.py client.txt client.srg
Nur für den Workflow client-sources.yml (Quelltext zum Nachschlagen).
"""
import re
import sys

PRIMS = {"void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S", "int": "I", "long": "J",
         "float": "F", "double": "D"}


def parse(path):
    classes = {}            # named -> obf
    members = []            # (named_owner, kind, named_type_or_ret, name, args, obf)
    owner = None
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            if not line.startswith(" "):
                named, obf = line.rstrip().rstrip(":").split(" -> ")
                classes[named] = obf
                owner = named
                continue
            body, obf = line.strip().split(" -> ")
            m = re.match(r"(?:\d+:\d+:)?(\S+) ([^ (]+)\((.*)\)(?::\d+:\d+)?$", body)
            if m:
                members.append((owner, "M", m.group(1), m.group(2), m.group(3), obf))
            else:
                typ, name = body.split(" ")
                members.append((owner, "F", typ, name, None, obf))
    return classes, members


def desc(typ, classes, obf):
    dims = 0
    while typ.endswith("[]"):
        dims += 1
        typ = typ[:-2]
    if typ in PRIMS:
        d = PRIMS[typ]
    else:
        name = classes.get(typ, typ) if obf else typ
        d = "L" + name.replace(".", "/") + ";"
    return "[" * dims + d


def main(src, dst):
    classes, members = parse(src)
    with open(dst, "w", encoding="utf-8") as out:
        for named, obf in classes.items():
            out.write("CL: %s %s\n" % (obf.replace(".", "/"), named.replace(".", "/")))
        for owner, kind, typ, name, args, obf in members:
            o_obf = classes[owner].replace(".", "/")
            o_named = owner.replace(".", "/")
            if kind == "F":
                out.write("FD: %s/%s %s/%s\n" % (o_obf, obf, o_named, name))
                continue
            if name in ("<init>", "<clinit>"):
                continue
            arg_list = [a for a in args.split(",") if a] if args else []
            d_obf = "(" + "".join(desc(a, classes, True) for a in arg_list) + ")" + desc(typ, classes, True)
            d_named = "(" + "".join(desc(a, classes, False) for a in arg_list) + ")" + desc(typ, classes, False)
            out.write("MD: %s/%s %s %s/%s %s\n" % (o_obf, obf, d_obf, o_named, name, d_named))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
