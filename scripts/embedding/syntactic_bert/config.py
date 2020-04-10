# -*- coding: utf-8 -*-


class Config(object):

    # [Network]
    n_bert_hidden = 768
    word_dropout = 0.33
    bert_dropout = 0.33
    n_mlp_arc = 500
    n_mlp_rel = 100
    mlp_dropout = 0.33

    # [Optimizer]
    lr = 2e-5
    beta_1 = 0.9
    beta_2 = 0.9
    epsilon = 1e-12
    weight_decay = 0.01
    decay = .75
    decay_steps = 5000

    # [Run]
    batch_size = 8
    epochs = 1000
    patience = 100
    gradient_accumulation_steps = 1
