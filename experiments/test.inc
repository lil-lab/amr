# Testing objects
type=chart.logger id=execLogger outputDir=%{chartLogs} prefix=test
type=tester.exec.amr id=tester
type=exec.amr parser=parser model=model id=exec logger=execLogger filterFactory=filterFactory vocab=%{resDir}/vocab.extended breakTies=true
## Testing statistics
type=test.stats.smatch id=statsSmatch smatch=%{smatchPath} rounds=20 timeout=10000
type=test.stats.exact id=statsExact
