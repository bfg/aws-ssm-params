package com.github.bfg.awsssmparamsenv


import spock.lang.Specification

class MainSpec extends Specification {
    PrintStream out = new MyPrintStream()

    def setup() {
        Main.output = out.clear()
    }

    def cleanup() {
        Main.output = System.out
    }

    def "[#arg] should display version"() {
        when:
        def res = Main.runMain(arg)
        def lines = out.lines()

        then:
        res == 0
        lines.size() == 1

        with(lines.first()) {
            !it.isBlank()
            it.endsWith('-SNAPSHOT') || it.matches('^\\d+\\..*')
        }

        where:
        arg << ['-V', '--version']
    }

    def "[#arg] should display help"() {
        when:
        def res = Main.runMain(arg)
        def lines = out.lines()

        then:
        res == 0
        lines.size() > 40

        with(lines.first()) {
            !it.isBlank()
            it.contains('Usage: ')
        }

        where:
        arg << ['-h', '--help']
    }

    def '[fmt: exports] should fetch parameters in exports format'() {
        given:
        def args =cliArgs('-rtlsu', '/test').toList()
        if (!fmt.isBlank()) args.addAll(['-F', fmt])

        when:
        def res = Main.runMain(args.toArray(new String[0]))
        def text = out.toString()

        then:
        res == 0

        text.trim() == '''
export 'ALPHA_DISPLAY_NAME=Alpha Display Name\'
export 'ALPHA_DUPLICATE=alpha-duplicate\'
export 'ALPHA_TOKEN=alpha-token\'
export 'BETA_DUPLICATE=beta-duplicate\'
export 'BETA_ENDPOINT=beta-endpoint\'
export 'BETA_PASSWORD=beta-password\'
export 'GAMMA_0BAZ=gamma-zero-baz\'
export 'GAMMA_API_KEY=gamma-api_key\'
export 'GAMMA_ENABLED=gamma-enabled\'
export 'GAMMA_FOO_BAR_BAZ=gamma-foo-bar-baz\'
export 'SHELL_INJECTION_A=a=\\`ls /etc\\`\'
export 'SHELL_INJECTION_B=b=\\$(ls /etc)\'
export 'SHELL_INJECTION_C=\\"x\'
export 'SHELL_INJECTION_D=x\\"\'
export 'SHELL_INJECTION_E=\\"x\\"\'
export 'SHELL_INJECTION_F='\\''x\\"\'
export 'SHELL_INJECTION_G=\\"'\\''x\'
export 'SHELL_INJECTION_H=\\$HOME\'
export 'SHELL_INJECTION_I=eval x=\\$((1 + 1))\'
'''.trim()

        where:
        fmt << ['exports', '']
    }

    def '[fmt: exports] should fetch parameters in dotenv format'() {
        given:
        def args =cliArgs('-tlsu', '--recursive', '-F', 'dotenv', '/test')

        when:
        def res = Main.runMain(args)
        def text= out.toString()

        then:
        res == 0
        text.trim() == '''
ALPHA_DISPLAY_NAME="Alpha Display Name"
ALPHA_DUPLICATE="alpha-duplicate"
ALPHA_TOKEN="alpha-token"
BETA_DUPLICATE="beta-duplicate"
BETA_ENDPOINT="beta-endpoint"
BETA_PASSWORD="beta-password"
GAMMA_0BAZ="gamma-zero-baz"
GAMMA_API_KEY="gamma-api_key"
GAMMA_ENABLED="gamma-enabled"
GAMMA_FOO_BAR_BAZ="gamma-foo-bar-baz"
SHELL_INJECTION_A="a=\\`ls /etc\\`"
SHELL_INJECTION_B="b=\\$(ls /etc)"
SHELL_INJECTION_C="\\"x"
SHELL_INJECTION_D="x\\""
SHELL_INJECTION_E="\\"x\\""
SHELL_INJECTION_F="'\\''x\\""
SHELL_INJECTION_G="\\"'\\''x"
SHELL_INJECTION_H="\\$HOME"
SHELL_INJECTION_I="eval x=\\$((1 + 1))"
'''.trim()
    }

    def cliArgs(String... args) {
        def res = ['--endpoint=http://localhost:4566', '--region=us-east-1'] + args.toList()
        res.toArray(new String[0])
    }

    static class MyPrintStream extends PrintStream {
        private static final baos = new ByteArrayOutputStream()

        MyPrintStream() {
            super(baos, true)
        }

        List<String> lines() {
            baos.flush()
            baos.toString().lines().toList()
        }

        MyPrintStream clear() {
            baos.reset()
            return this
        }

        @Override
        String toString() {
            return baos.toString()
        }
    }
}
