#!/usr/bin/env python3
"""Build Edward Zhao's Project 2.4 testing contribution (requires reportlab)."""
import json
from pathlib import Path
from xml.sax.saxutils import escape
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_LEFT
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, KeepTogether

HERE = Path(__file__).resolve().parent
DATA = json.loads((HERE / 'cases.json').read_text())
RESULTS = json.loads((HERE / 'results.json').read_text())
OUT = HERE / 'Team04_Project2.4_EdwardZhao_Testing.pdf'
styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name='BodyCustom', fontName='Helvetica', fontSize=10, leading=14, spaceAfter=8))
styles.add(ParagraphStyle(name='SmallCustom', fontName='Helvetica', fontSize=8.5, leading=12, spaceAfter=6, wordWrap='CJK'))
styles.add(ParagraphStyle(name='CodeCustom', fontName='Courier', fontSize=8, leading=12, spaceAfter=10, wordWrap='CJK'))
styles['Title'].textColor = colors.HexColor('#17324d')
styles['Heading1'].textColor = colors.HexColor('#17324d')
styles['Heading2'].textColor = colors.HexColor('#245a72')
story=[]
def p(text, style='BodyCustom'):
    story.append(Paragraph(text, styles[style]))
def code(text):
    p(escape(text), 'CodeCustom')
def page(title):
    story.append(PageBreak()); p(title,'Heading1')
def table(rows, widths):
    t=Table([[Paragraph(escape(str(c)), styles['SmallCustom']) for c in r] for r in rows],colWidths=widths,hAlign='LEFT')
    t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#e9eff4')),('VALIGN',(0,0),(-1,-1),'TOP'),('BOTTOMPADDING',(0,0),(-1,-1),7),('TOPPADDING',(0,0),(-1,-1),7),('LINEBELOW',(0,0),(-1,0),0.8,colors.HexColor('#17324d')),('LINEBELOW',(0,1),(-1,-1),0.3,colors.HexColor('#ccd5dd'))]))
    story.append(t);story.append(Spacer(1,12))

p('SplitSmart', 'Title')
p('Project 2.4 - Testing Document', 'Heading1')
p('Team 04 | Project A | Edward Zhao contribution', 'Heading2')
p('Prepared November 13, 2026. Branch: EdwardTesting.')
table([['Team member','USC email'],['Zachary Chung','zchung@usc.edu'],['Aux Devarapalli','adevarap@usc.edu'],['Edward Zhao','ebzhao@usc.edu'],['Swayam Parekh','swayampa@usc.edu'],['Brian Shi','brianshi@usc.edu']],[220,284])
p('1. Preface', 'Heading1')
p('This document records 10 executable JUnit white-box tests and 10 executable Espresso black-box tests for SplitSmart. <b>Edward Zhao wrote tests EZ-W01 through EZ-W10 and EZ-B01 through EZ-B10.</b> Each case below identifies its method, inputs, expected behavior, rationale, result, and any discovered defect.')
p('White-box tests target expense-validation branches and boundaries (FR 2.2, FR 2.3, FR 2.6, FR 3.4, FR 3.6). Black-box tests drive the real registration and login screens through user inputs and visible messages (FR 1.1 and FR 1.3). They do not call validators or ViewModels to establish the expected outcome.')
p('<b>Scope:</b> This is Edward\'s contribution, not a claim that the entire team submission is complete. The five-member assignment requires at least 25 tests of each type and individual contributions from every member. The 10 new black-box tests do not satisfy that team-wide minimum by themselves. Four pre-existing Android panel tests are component tests and are not counted here as system-level black-box tests. Other teammates\' test ownership has not been reassigned.')
p('Team names and emails were taken from the existing Project 2.3 implementation document. Existing tests are preserved.')
page('2. Execute the tests')
p('Prerequisites: JDK 17 or 21, Android SDK Platform 36, the repository\'s Gradle wrapper, and app/google-services.json as described in README.md. Run commands from the project root. For Espresso, start a disposable Android emulator (API 26+) and unlock it. Verification used Pixel_4, Android 13 / API 33, JBR 21.0.11, and Gradle 8.14.3.')
p('All local unit tests and measured coverage', 'Heading2')
code('./gradlew jacocoDebugUnitTestReport')
p('The report includes business-logic and model classes only. HTML: app/build/reports/jacoco/jacocoDebugUnitTestReport/html/index.html. XML: app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml.', 'SmallCustom')
p('Edward\'s 10 white-box tests', 'Heading2')
code("./gradlew testDebugUnitTest --tests 'com.team04.splitsmart.logic.EdwardExpenseValidationTest'")
p('For a single case, append .METHOD to the class name in --tests, using the method listed in section 4.')
p('Edward\'s 10 black-box tests', 'Heading2')
code('./gradlew connectedDebugAndroidTest -Psplitsmart.emulatorHost=10.0.2.2 -Pandroid.testInstrumentationRunnerArguments.class=com.team04.splitsmart.ui.EdwardRegistrationBlackBoxTest')
p('For a single case, append #METHOD to the class name. Run the full Android suite by omitting the class argument. Each case launches and closes a fresh ActivityScenario; the fixture signs out the emulator app session first. Use a disposable emulator because this signs out any existing local session.')
p('The build flag isolates Firebase SDKs from the shared project. These validation cases stop before any successful authentication or database write, so they need no live Firebase emulator server or seeded accounts. The tests fail early if the isolation flag is absent. They do not test successful login, duplicate accounts, backend security, or remote persistence.')
p('Expected results and evidence', 'Heading2')
p('Every listed case passed. Captured JUnit XML, Android result XML, and JaCoCo XML are stored under docs/testing-document/evidence/. results.json summarizes those files. A focused Gradle run overwrites earlier reports; regenerate the full coverage command before quoting full-suite totals.')

for kind,label,section,clazz,path in [
 ('black','Black-box testing',3,'EdwardRegistrationBlackBoxTest','app/src/androidTest/java/com/team04/splitsmart/ui/'),
 ('white','White-box testing',4,'EdwardExpenseValidationTest','app/src/test/java/com/team04/splitsmart/logic/')]:
    cases=DATA[kind]
    for batch in range(0,10,5):
        page(f'{section}. {label}' + ('' if batch==0 else ' (continued)'))
        p('Author of every case on this page: <b>Edward Zhao (ebzhao@usc.edu)</b>.', 'SmallCustom')
        p('File: '+escape(path+clazz+'.java'), 'SmallCustom')
        p('Execution: use the class command in section 2, or its single-method variant. Result: <b>PASS</b> for each case.', 'SmallCustom')
        for ident,method,title,action,rationale,bug in cases[batch:batch+5]:
            p(f'{ident} | {escape(title)}', 'Heading2')
            p('<b>Method:</b> '+escape(method), 'SmallCustom')
            p('<b>Input and expected outcome:</b> '+escape(action),'SmallCustom')
            p('<b>Rationale:</b> '+escape(rationale),'SmallCustom')
            p('<b>Defect / change:</b> '+escape(bug),'SmallCustom')

page('5. Results, coverage, and fixes')
table([['Run','Passed','Failed / skipped'],['Local JUnit suite',RESULTS['unit']['tests'],'0 / 0'],['Edward white-box subset',10,'0 / 0'],['Android suite',RESULTS['android']['tests'],'0 / 0'],['Edward black-box subset',10,'0 / 0']],[280,90,134])
p('Coverage criteria', 'Heading2')
p('The white-box design targets branch outcomes, boundary values, early returns, and successful paths in InputValidator.validateExpense. Line coverage measures executed executable source lines; branch coverage measures executed decision outcomes. JaCoCo 0.8.13 collected the following values from the full local unit suite, including pre-existing tests. These are not coverage percentages attributable solely to Edward\'s tests.')
rows=[['Scope','Line coverage','Branch coverage']]
for label,key in [('Business logic + models','overall'),('InputValidator class','validator'),('validateExpense method','expense')]:
    d=RESULTS['coverage'][key]
    def fmt(k):
        c=d[k]['covered']; total=c+d[k]['missed'];return f'{c}/{total} ({100*c/total:.1f}%)'
    rows.append([label,fmt('LINE'),fmt('BRANCH')])
table(rows,[220,142,142])
p('Coverage excludes UI, ViewModels, Firebase repositories, generated classes, and Android instrumentation execution. It does not demonstrate full-app or end-to-end coverage. Missing branches remain; no 100% coverage claim is made.')
p('Confirmed defects and implementation changes', 'Heading2')
p('<b>D1 - negative percentages:</b> validateExpense checked the percentage sum but not each value\'s sign. EZ-W06 reproduced acceptance of -10% and 110%. A per-share negative-value check now returns the split-field error before summation.')
p('<b>D2 - integer overflow:</b> a large basis-point sum could wrap to 10,000 in an int. EZ-W07 reproduced this acceptance. The accumulator now uses long, and BigDecimal formats the exact summed percentage without narrowing it back to int. Both regression tests failed before the production fix and pass afterward.')
p('Changed production file: app/src/main/java/com/team04/splitsmart/logic/validation/InputValidator.java.', 'SmallCustom')
page('6. Contribution map and remaining team work')
table([['Member','New white-box tests','New black-box tests'],['Edward Zhao','EZ-W01 - EZ-W10','EZ-B01 - EZ-B10'],['Other team members','Not assigned by this contribution','Not assigned by this contribution']],[180,162,162])
p('This contribution adds exactly 20 test methods: 10 local JUnit methods and 10 Espresso methods. Boundary checks within one method count as one test, not multiple tests. The full local suite contains '+str(RESULTS['unit']['tests'])+' methods; the full Android suite contains '+str(RESULTS['android']['tests'])+' methods including four pre-existing panel checks.')
p('Before the team submits', 'Heading2')
p('Integrate the other members\' documented tests, confirm the required minimum of 25 executable tests of each type, and record which teammate wrote each case. Add broader system scenarios for successful registration/login, group and member management, expense creation/edit/deletion, balances, settlements, history, and summaries. This document deliberately reports the narrower scope actually tested here.')
p('Reproducibility', 'Heading2')
p('The PDF source is build_report.py with cases.json and results.json. Rebuild with Python 3 and reportlab:')
code('python3 docs/testing-document/build_report.py')
p('The source archive includes the project\'s tracked source and documentation, including this PDF; it excludes Git history, build products, local SDK paths, and local caches. It is a snapshot for review and team integration, not a representation that all team submission requirements are complete.')

def footer(canvas,doc):
    canvas.saveState();canvas.setStrokeColor(colors.HexColor('#ccd5dd'));canvas.line(54,43,558,43)
    canvas.setFont('Helvetica',8);canvas.setFillColor(colors.HexColor('#445566'))
    canvas.drawString(54,30,'SplitSmart | Team 04 | Edward Zhao | Project 2.4')
    canvas.drawRightString(558,30,str(doc.page));canvas.restoreState()
SimpleDocTemplate(str(OUT),pagesize=(612,792),rightMargin=54,leftMargin=54,topMargin=48,bottomMargin=58,title='SplitSmart Project 2.4 - Edward Zhao Testing',author='Edward Zhao').build(story,onFirstPage=footer,onLaterPages=footer)
print(OUT)
