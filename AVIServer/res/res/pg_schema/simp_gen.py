def simple_gen(n):
    n = yield n+1
    n = yield n+2
    n = yield n+3

if __name__=='__main__':
    simp_gen = simple_gen(0)
    print simp_gen.next()
    print simp_gen.send(None)
    print simp_gen.send(6)
    print simp_gen.send(None)
    print simp_gen.next()
