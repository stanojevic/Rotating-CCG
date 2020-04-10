#with open('/Users/Jimmy/train.conllx') as f:
import os
import glob
from collections import defaultdict
from tabulate import tabulate

used = {"UD_Afrikaans-AfriBooms",
		"UD_Arabic-NYUAD",
		"UD_Arabic-PADT",
		"UD_Armenian-ArmTDP",
		"UD_Basque-BDT",
		"UD_Belarusian-HSE",
		"UD_Bulgarian-BTB",
		"UD_Catalan-AnCora",
		"UD_Chinese-GSD",
		"UD_Croatian-SET",
		"UD_Czech-CAC",
		"UD_Czech-CLTT",
		"UD_Czech-FicTree",
		"UD_Czech-PDT",
		"UD_Danish-DDT",
		"UD_Dutch-Alpino",
		"UD_Dutch-LassySmall",
		"UD_English-ESL",
		"UD_English-EWT",
		"UD_English-GUM",
		"UD_English-LinES",
		"UD_English-ParTUT",
		"UD_Estonian-EDT",
		"UD_Finnish-FTB",
		"UD_Finnish-TDT",
		"UD_French-GSD",
		"UD_French-ParTUT",
		"UD_French-Sequoia",
		"UD_Galician-CTG",
		"UD_Galician-TreeGal",
		"UD_German-GSD",
		"UD_Greek-GDT",
		"UD_Hebrew-HTB",
		"UD_Hindi-HDTB",
		"UD_Hungarian-Szeged",
		"UD_Indonesian-GSD",
		"UD_Irish-IDT",
		"UD_Italian-ISDT",
		"UD_Italian-ParTUT",
		"UD_Japanese-GSD",
		"UD_Kazakh-KTB",
		"UD_Korean-GSD",
		"UD_Korean-Kaist",
		"UD_Latin-ITTB",
		"UD_Latin-PROIEL",
		"UD_Latin-Perseus",
		"UD_Latvian-LVTB",
		"UD_Lithuanian-HSE",
		"UD_Marathi-UFAL",
		"UD_Norwegian-Bokmaal",
		"UD_Norwegian-Nynorsk",
		"UD_Persian-Seraji",
		"UD_Polish-LFG",
		"UD_Polish-SZ",
		"UD_Portuguese-Bosque",
		"UD_Portuguese-GSD",
		"UD_Romanian-RRT",
		"UD_Russian-GSD",
		"UD_Russian-SynTagRus",
		"UD_Serbian-SET",
		"UD_Slovak-SNK",
		"UD_Slovenian-SSJ",
		"UD_Spanish-AnCora",
		"UD_Spanish-GSD",
		"UD_Swedish-LinES",
		"UD_Swedish-Talbanken",
		"UD_Tamil-TTB",
		"UD_Telugu-MTG",
		"UD_Turkish-IMST",
		"UD_Urdu-UDTB",
		"UD_Vietnamese-VTB"}

used_lang = set()
for corpus in used:
	lang = corpus.split('-')[0][3:]
	used_lang.add(lang)
            
corpora = defaultdict(lambda: [0, 0, 0])
language = defaultdict(lambda: [0, 0, 0])
folder_path = "/Users/Jimmy/Downloads/Universal_Dependencies_2.3/ud-treebanks-v2.3/"
for index, dataset in enumerate(['train', 'dev', 'test']):
	for file_path in sorted(glob.glob(folder_path + '/*/*{}.conllu'.format(dataset))):
		file = file_path.split('/')[-2]
		# if file not in used:
		# 	continue
		with open(file_path) as f_in:
			count = 0
			for line in f_in:
				line = line.strip()
				if line.startswith('# text ='):
					count += 1
		corpora[file][index] = count
		language[file.split('-')[0][3:]][index] += count

print('## Everything\n')
headers = ['Language', 'Train', 'Dev', 'Test', 'Total']
print('**Languages by Frequency**\n')
print(tabulate([[k] + v + [sum(v)] for k, v in sorted(language.items(), key=lambda x: -sum(x[1]))], tablefmt='github', headers=headers))

print(' ')
print('**Languages Alphabetical**\n')
print(tabulate([[k] + v + [sum(v)] for k, v in sorted(language.items())], tablefmt='github', headers=headers))

headers = ['Corpus', 'Train', 'Dev', 'Test', 'Total']
print(' ')
print('**Corpora by Frequency**\n')
print(tabulate([[k] + v + [sum(v)] for k, v in sorted(corpora.items(), key=lambda x: -sum(x[1]))], tablefmt='github', headers=headers))

print(' ')
print('**Corpora Alphabetical**\n')
print(tabulate([[k] + v + [sum(v)] for k, v in sorted(corpora.items())], tablefmt='github', headers=headers))



print('## The Ones Used\n')
headers = ['Language', 'Train', 'Dev', 'Test', 'Total']
print('**Languages by Frequency**\n')
print(tabulate([[k] + v + [sum(v)] for k, v in sorted(language.items(), key=lambda x: -sum(x[1])) if k in used_lang], tablefmt='github', headers=headers))

print(' ')
print('**Languages Alphabetical**\n')
print(tabulate([[k] + v + [sum(v)] for k, v in sorted(language.items()) if k in used_lang], tablefmt='github', headers=headers))

headers = ['Corpus', 'Train', 'Dev', 'Test', 'Total']
print(' ')
print('**Corpora by Frequency**\n')
print(tabulate([[k] + v + [sum(v)] for k, v in sorted(corpora.items(), key=lambda x: -sum(x[1])) if k in used], tablefmt='github', headers=headers))

print(' ')
print('**Corpora Alphabetical**\n')
print(tabulate([[k] + v + [sum(v)] for k, v in sorted(corpora.items()) if k in used], tablefmt='github', headers=headers))

# corpora = {}
# language = defaultdict(int)
# with open('omg') as f:
# 	name, val = None, None
# 	for line in f:
# 		line = line.strip()
# 		if name and val:
# 			corpora[name] = val
# 			language[name.split('-')[0][3:]] += val
# 			name, val = None, None

# 		if line.startswith('*'):
# 			continue
# 		if line.startswith('UD'):
# 			name = line
# 		else:
# 			val = int(line)
# print(tabulate(sorted(language.items(), key=lambda x: x[1]), tablefmt='github'))


# path = "debugging/"
# d ={'UD_Catalan-AnCora': 25,
# 	'UD_Czech-CLTT': 17,
# 	'UD_Czech-PDT': 21,
# 	'UD_French-GSD': 27,
# 	'UD_French-ParTUT': 19,
# 	'UD_Italian-ISDT': 19,
# 	'UD_Italian-ParTUT': 19,
# 	'UD_Urdu-UDTB': 32}

# batch_size = 1
# for file, batch in d.items():
# 	folder_path = os.path.join(path, file)
# 	# print(folder_path)
# 	try:
# 		file_path = glob.glob(folder_path + '.conllx')[0]
# 	except:
# 		# print(file)
# 		assert 1 == 2
# 	print('***********************************************')
# 	print(file)
# 	print('***********************************************')
# 	with open(file_path) as f_in, open('debugging_one_sentence/' + file + '.conllx','w') as f_out:
# 		count = 0
# 		for line in f_in:
# 			line = line.strip()
# 			if line.startswith('# sent_id ='):
# 				count += 1
# 			if count > (batch + 1) * batch_size:
# 				break
# 			if count >= (batch - 1) * batch_size:
# 				if line.startswith('# text ='):
# 					print(line)
# 				f_out.write(line + '\n')
			
# 	print(' ')




# path = "/Users/Jimmy/Downloads/Universal_Dependencies_2.3/ud-treebanks-v2.3/"
# d ={'UD_Catalan-AnCora': 219,
# 	'UD_Czech-CLTT': 19,
# 	'UD_Czech-PDT': 4066,
# 	'UD_French-GSD': 402,
# 	'UD_French-ParTUT': 36,
# 	'UD_Italian-ISDT': 348,
# 	'UD_Italian-ParTUT': 36,
# 	'UD_Urdu-UDTB': 63}	

# batch_size = 16
# for file, batch in d.items():
# 	folder_path = os.path.join(path, file)
# 	try:
# 		file_path = glob.glob(folder_path + '/*train.conllu')[0]
# 	except:
# 		print(file)
# 		assert 1 == 2
# 	print('***********************************************')
# 	print(file)
# 	print('***********************************************')
# 	with open(file_path) as f_in, open('debugging/' + file + '.conllx','w') as f_out:
# 		count = 0
# 		for line in f_in:
# 			line = line.strip()
# 			if line.startswith('# text ='):
# 				count += 1
# 			# if count >= (batch - 1) * batch_size:
# 			# 	if line.startswith('# text ='):
# 			# 		print(line)
# 			# 	f_out.write(line + '\n')
# 			# if count > (batch + 2) * batch_size:
# 			# 	break
# 	print(count)




# with open('data/train.conllx') as f:
# 	lines = []
# 	count = 0
# 	for line in f:
# 		line = line.strip()
# 		#if line.startswith('# text ='):
# 		if len(line) == 0:
# 			count += 1
# 	# 	if count >= 11551:
# 	# 		if line.startswith('# text ='):
# 	# 			lines.append(line)
# 	# 	if count >= 11551 + 20:
# 	# 		break

# 	# for line in lines:
# 	# 	print(line)
# print(count)