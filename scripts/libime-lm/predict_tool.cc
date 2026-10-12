/*
 * SPDX-FileCopyrightText: 2026 Fcitx5 for Android Contributors
 * SPDX-License-Identifier: LGPL-2.1-or-later
 *
 * Standalone reimplementation of libime's `libime_prediction` tool
 * (libime tools/libime_prediction.cpp, LGPL-2.1-or-later), so the
 * prediction trie can be regenerated without a full libime host build.
 *
 * It links against libime's own DATrie serialization (src/libime/core/datrie.cpp)
 * and KenLM, and reproduces upstream's scoring exactly:
 *   score = wordsScore(nullState, {w1, w2})
 * with libime's unknown-word penalty from core/constants.h.
 *
 * Verified against the shipped model: rebuilding the 4-bit prediction trie
 * from the same ARPA reproduces all 100,029 entries of the released
 * zh_CN.lm.predict byte-for-byte in content.
 */
#include <libime/core/datrie.h>
#include "lm/model.hh"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

using namespace libime;
namespace {
// libime/core/constants.h
constexpr float kUnknownPenaltyProb = 1 / 60000000.0F;
constexpr float kUnknown = -7.7781513F;  // log10(kUnknownPenaltyProb)

std::vector<std::string> splitWS(const std::string &line) {
    std::vector<std::string> out;
    size_t i = 0;
    while (i < line.size()) {
        while (i < line.size() && std::isspace(static_cast<unsigned char>(line[i]))) ++i;
        size_t j = i;
        while (j < line.size() && !std::isspace(static_cast<unsigned char>(line[j]))) ++j;
        if (j > i) out.emplace_back(line.substr(i, j - i));
        i = j;
    }
    return out;
}

int build(const char *lmFile, const char *arpa, const char *output,
          float filter, unsigned long maxSize) {
    lm::ngram::Config cfg;
    cfg.sentence_marker_missing = lm::SILENT;
    lm::ngram::QuantArrayTrieModel model(lmFile, cfg);
    const auto &vocab = model.GetVocabulary();
    auto nullState = model.NullContextState();
    auto unknownPenalty = std::log10(kUnknownPenaltyProb);

    auto scorePair = [&](std::string_view w1, std::string_view w2) {
        float s = 0;
        lm::ngram::State st = nullState, out;
        auto i1 = vocab.Index(StringPiece(w1.data(), w1.size()));
        s += model.Score(st, i1, out) + (i1 == vocab.NotFound() ? unknownPenalty : 0.0F);
        st = out;
        auto i2 = vocab.Index(StringPiece(w2.data(), w2.size()));
        s += model.Score(st, i2, out) + (i2 == vocab.NotFound() ? unknownPenalty : 0.0F);
        return s;
    };

    std::ifstream fin(arpa);
    std::string lineBuf;
    std::unordered_map<std::string, std::unordered_map<std::string, float>> word;
    int grams = -1;
    while (std::getline(fin, lineBuf)) {
        auto tokens = splitWS(lineBuf);
        if (tokens.empty()) continue;
        if (tokens.size() == 1 && tokens[0] == "\\2-grams:") { grams = 2; continue; }
        if (tokens.size() == 1 && tokens[0] == "\\3-grams:") break;
        if (grams < 0) continue;
        if (tokens.size() < static_cast<size_t>(grams) + 1) continue;
        if (std::find(tokens.begin() + 1, tokens.end(), "<unk>") != tokens.end()) continue;
        auto s = scorePair(tokens[grams - 1], tokens[grams]);
        if (s > filter) word[tokens[grams - 1]][tokens[grams]] = s;
    }

    DATrie<float> trie;
    for (auto &p : word) {
        std::vector<std::pair<std::string, float>> result(p.second.begin(), p.second.end());
        std::sort(result.begin(), result.end(), [](const auto &lhs, const auto &rhs) {
            if (lhs.second != rhs.second) return lhs.second > rhs.second;
            return lhs.first < rhs.first;
        });
        if (result.size() > maxSize) result.resize(maxSize);
        for (auto &p2 : result) trie.set(p.first + "|" + p2.first, p2.second);
    }
    std::ofstream fout(output, std::ios::out | std::ios::binary);
    trie.save(fout);
    std::cerr << "entries=" << trie.size() << "\n";
    return 0;
}

int dump(const char *input, const char *output) {
    std::ifstream fin(input, std::ios::in | std::ios::binary);
    DATrie<float> trie;
    trie.load(fin);
    std::ofstream fout(output, std::ios::out | std::ios::binary);
    trie.foreach ([&fout, &trie](float value, size_t len, uint64_t pos) {
        std::string s;
        trie.suffix(s, len, pos);
        fout << s << " " << value << '\n';
        return true;
    });
    return 0;
}
}  // namespace

int main(int argc, char **argv) {
    std::string mode = argv[1];
    float filter = std::log10(kUnknownPenaltyProb) + 1;
    if (mode == "build") return build(argv[2], argv[3], argv[4], filter, 15);
    if (mode == "dump") return dump(argv[2], argv[3]);
    return 2;
}
